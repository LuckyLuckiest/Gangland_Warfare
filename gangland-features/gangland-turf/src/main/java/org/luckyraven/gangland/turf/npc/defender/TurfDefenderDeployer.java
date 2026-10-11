package org.luckyraven.gangland.turf.npc.defender;

import lombok.CustomLog;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.HologramTrait;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.gangland.turf.events.TurfDefenderDownEvent;
import org.luckyraven.gangland.turf.npc.defender.GarrisonPosts.Choice;
import org.luckyraven.gangland.turf.npc.defender.GarrisonPosts.Post;
import org.luckyraven.gangland.npc.NpcRole;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.keystone.util.ChatUtil;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Spawns and ticks ephemeral turf defenders during a capture by reusing the existing civilian NPC infrastructure. No
 * bespoke entity types — every defender is a {@link CivilianNpc} of the configured type id from {@code civilians.yml},
 * transitioned into {@link CivilianState#COMBAT} with its target pointed at whichever online attacker is closest. The
 * civilian system already handles pathing, attack cadence, and entity cleanup; this deployer just owns the spawn /
 * target-rebind / recall lifecycle bound to a specific turf.
 *
 * <p>One {@link Group} per turf. A garrison group ({@link #startGarrison}) defends in waves: the 5-tick task spawns the
 * next wave at the hidden post farthest from the attackers, takes each guard out of the reserve only after it spawned,
 * keeps guards on a leash of the turf plus a margin, and on {@link #standDown} lets the survivors shout, walk to a post
 * and leave, handing their count back for the reserve. The targeting tick picks the closest live challenger gang member
 * within {@code targetingRadius} inside the leash, sets it as the civilian's combat target, and forces a transition
 * into COMBAT if not already there.
 */
@CustomLog
public final class TurfDefenderDeployer implements BeanLifecycle {

	private static final long TICK_PERIOD = 5L;
	/** How long a wave waits for a post no attacker can see before it uses the farthest one anyway. */
	private static final long VISIBLE_DELAY_MS = 3_000L;
	private static final double EYE_HEIGHT = 1.6;
	private static final double AT_POST_SQUARED = 4.0;

	private final JavaPlugin           plugin;
	private final CivilianService      civilianService;
	private final CivilianSpawnManager spawnManager;

	private final Map<Integer, Group> byTurfId = new HashMap<>();
	/** Entity UUID to turf id, so friendly-fire lookups on every weapon impact are O(1) (TF-35). */
	private final Map<UUID, Integer>  turfByEntity = new HashMap<>();
	/** Guard to turf id, so {@link #markKilled} is O(1). */
	private final Map<CivilianNpc, Integer> turfByNpc = new IdentityHashMap<>();
	/** Guards whose death was reported by {@link #markKilled}; only these count as "down" (T-251). */
	private final Set<CivilianNpc> killed = Collections.newSetFromMap(new IdentityHashMap<>());
	/** Callsign number of each tracked guard, so a recycled guard keeps its number and its role. */
	private final Map<CivilianNpc, Integer> numberByNpc = new IdentityHashMap<>();
	/** When each tracked guard was last recycled (or its predecessor was), for the once-per-window rule. */
	private final Map<CivilianNpc, Long>    recycledAt  = new IdentityHashMap<>();
	/** Composition role names already reported as unknown, so a bad name warns once per boot, not per guard. */
	private final Set<String>        unknownRoles = new HashSet<>();
	private       BukkitTask          tickTask;
	private       BooleanSupplier     citizens = NpcSupport::available;

	public TurfDefenderDeployer(JavaPlugin plugin,
	                            CivilianService civilianService,
	                            CivilianSpawnManager spawnManager) {
		this.plugin          = plugin;
		this.civilianService = civilianService;
		this.spawnManager    = spawnManager;
	}

	public void start() {
		if (tickTask != null) return;
		tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
	}

	@Override
	public void onShutdown() {
		stop();
	}

	public void stop() {
		if (tickTask != null) {
			tickTask.cancel();
			tickTask = null;
		}
		for (Group g : byTurfId.values()) g.recallAll();
		byTurfId.clear();
		turfByEntity.clear();
		turfByNpc.clear();
		killed.clear();
		numberByNpc.clear();
		recycledAt.clear();
	}

	/** Test seam: whether Citizens counts as available. */
	void setCitizensAvailable(BooleanSupplier available) {
		this.citizens = available;
	}

	/**
	 * Arms a garrison on a contested turf. Nothing spawns here: the next targeting tick spawns the first wave, and each
	 * guard is taken out of the reserve only once it spawned. Without Citizens nothing is armed and nothing is spent
	 * (TF-46).
	 *
	 * @param challengers the players the guards fight, re-read on every tick
	 * @param lifespanSeconds safety lifespan of each guard, read on every tick (T-269)
	 */
	public void startGarrison(TurfGarrison garrison, Supplier<Set<UUID>> challengers, IntSupplier lifespanSeconds) {
		if (!citizens.getAsBoolean()) return;

		Group previous = byTurfId.get(garrison.turfId);
		if (previous != null && previous.garrison != null) {
			// T-751: a rival who stayed on the turf restarts the contest; the standing guards keep fighting
			// instead of being finished and respawned, so the contest holds rather than flapping.
			previous.garrison.standingDown = false;
			previous.garrison.onReturn     = null;
			previous.challengerMembers     = challengers;
			previous.lifespan              = lifespanSeconds;
			return;
		}
		if (previous != null) {
			finish(garrison.turfId, previous);
		}
		garrison.squads = new TurfGarrisonSquads(civilianService, garrison.turfId, garrison.tag);
		Group group = new Group(challengers, garrison.config.targetingRadius());
		group.garrison = garrison;
		group.lifespan = lifespanSeconds;
		byTurfId.put(garrison.turfId, group);
	}

	/**
	 * The contest ended. The survivors shout {@code Turf_Held} or {@code Turf_Lost}, walk to the nearest post and, after
	 * {@code Stand_Down_Seconds}, leave; {@code onReturn} then gets how many were still standing so the caller can put
	 * them back in the reserve. A turf without a garrison group does nothing.
	 */
	public void standDown(int turfId, boolean held, IntConsumer onReturn) {
		Group group = byTurfId.get(turfId);
		if (group == null) return;
		TurfGarrison garrison = group.garrison;
		if (garrison == null) {
			int alive = aliveCount(turfId);
			recall(turfId);
			onReturn.accept(alive);
			return;
		}
		if (garrison.standingDown) return;

		garrison.standingDown = true;
		garrison.onReturn     = onReturn;
		garrison.standDownAt  = System.currentTimeMillis() + garrison.config.standDownSeconds() * 1000L;
		String line = held ? "Turf_Held" : "Turf_Lost";
		for (TrackedDefender d : group.defenders) {
			if (!d.npc.isValid() || d.npc.isMarkedForRemoval()) continue;
			standDownOne(d.npc, garrison);
			civilianService.shout(d.npc, line, Map.of());
		}
	}

	/**
	 * Recall every defender deployed for {@code turfId}. Called on capture-complete and capture-failed so a finished
	 * contest cleans up its garrison even if some defenders are still alive.
	 */
	public void recall(int turfId) {
		Group group = byTurfId.remove(turfId);
		if (group != null) {
			for (TrackedDefender d : group.defenders) {
				untrack(d);
			}
			group.recallAll();
		}
	}

	/** T-251: records that this guard died (not merely vanished); a no-op for an NPC that is not a tracked guard. */
	public void markKilled(CivilianNpc npc) {
		if (turfByNpc.containsKey(npc)) {
			killed.add(npc);
		}
	}

	/** What a guard's death pays a rival: the gang that paid for it and the cash. */
	public record GuardBounty(int ownerGangId, int amount) {
	}

	/**
	 * What killing this guard pays a rival: {@code floor(price x Bounty_Share)} of what the owner paid for it, or
	 * {@code null} for an NPC that is not a tracked guard. The owner funded it already, so nothing is minted.
	 */
	@Nullable
	public GuardBounty bountyOf(CivilianNpc npc) {
		Integer turfId = turfByNpc.get(npc);
		Group   group  = turfId == null ? null : byTurfId.get(turfId);
		if (group == null || group.garrison == null) return null;
		TurfGarrison garrison = group.garrison;
		int amount = (int) Math.floor(garrison.guardPrice * Math.max(0.0, garrison.config.bountyShare()));
		return new GuardBounty(garrison.ownerGangId, amount);
	}

	/**
	 * Returns the turf id this entity is a defender for, or {@code -1} if the entity is not a tracked defender. Used by
	 * the friendly-fire listener to short-circuit damage from the owning gang.
	 */
	public int findOwningTurfId(Entity entity) {
		if (entity == null) return -1;
		return turfByEntity.getOrDefault(entity.getUniqueId(), -1);
	}

	/**
	 * The defenders of {@code turfId} that are still alive and not yet expired; empty when the turf has no group.
	 */
	public List<CivilianNpc> liveDefenders(int turfId) {
		Group group = byTurfId.get(turfId);
		if (group == null) return List.of();
		long now = System.currentTimeMillis();
		List<CivilianNpc> live = new ArrayList<>(group.defenders.size());
		for (TrackedDefender d : group.defenders) {
			if (d.npc.isValid() && !d.npc.isMarkedForRemoval() && now < d.expiresAt()) live.add(d.npc);
		}
		return live;
	}

	/**
	 * Bound to {@link CivilianService} via constructor injection so it stays referenced — used to acknowledge the
	 * dependency chain even though deploys go through {@link CivilianSpawnManager}. Civilians spawned via the spawn
	 * manager auto-register with the service's tick loop.
	 */
	@SuppressWarnings("unused")
	public CivilianService getCivilianService() {
		return civilianService;
	}

	/** How many guards of {@code turfId} are alive, valid and not expired right now (0 when none were deployed). */
	public int aliveCount(int turfId) {
		return liveDefenders(turfId).size();
	}

	/** Adds one guard to its turf's group and the lookup indexes; package-private for tests. */
	void track(int turfId, CivilianNpc npc, long deployedAt, IntSupplier lifespanSeconds) {
		Group group = byTurfId.computeIfAbsent(turfId, k -> new Group(Set::of, 32.0));
		LivingEntity entity = npc.getEntity();
		UUID entityId = entity == null ? null : entity.getUniqueId();
		group.defenders.add(new TrackedDefender(npc, deployedAt, lifespanSeconds, entityId));
		group.deployed++;
		turfByNpc.put(npc, turfId);
		if (entityId != null) {
			turfByEntity.put(entityId, turfId);
		}
	}

	private void untrack(TrackedDefender d) {
		killed.remove(d.npc);
		numberByNpc.remove(d.npc);
		recycledAt.remove(d.npc);
		turfByNpc.remove(d.npc);
		if (d.entityId != null) {
			turfByEntity.remove(d.entityId);
		}
	}

	/** Package-private for tests (W2-K). */
	void tick() {
		long now = System.currentTimeMillis();

		for (Iterator<Map.Entry<Integer, Group>> entries = byTurfId.entrySet().iterator(); entries.hasNext(); ) {
			Map.Entry<Integer, Group> entry   = entries.next();
			Group                     group   = entry.getValue();
			TurfGarrison              garrison = group.garrison;

			int down = 0;
			int lost = 0;
			// Reap dead / expired defenders first so targeting only runs over live ones.
			for (Iterator<TrackedDefender> it = group.defenders.iterator(); it.hasNext(); ) {
				TrackedDefender d = it.next();
				if (!d.npc.isValid() || d.npc.isMarkedForRemoval() || now >= d.expiresAt()) {
					boolean died = killed.contains(d.npc);
					if (now < d.expiresAt()) {
						lost++;
						if (died) {
							down++;
						} else {
							// T-251: unloaded or despawned, not killed - drop it without a defender-down alert
							log.debug("Turf {}: guard left without dying (despawned or unloaded); not counted as down", entry.getKey());
						}
					}
					if (d.npc.isValid()) d.npc.markForRemoval();
					untrack(d);
					it.remove();
				}
			}
			if (down > 0) {
				Bukkit.getPluginManager().callEvent(
						new TurfDefenderDownEvent(entry.getKey(), group.defenders.size(), group.deployed));
			}

			if (garrison != null && garrison.standingDown) {
				if (now >= garrison.standDownAt) {
					entries.remove();
					release(group);
				} else {
					for (TrackedDefender d : group.defenders) {
						standDownOne(d.npc, garrison);
					}
				}
				continue;
			}

			if (garrison != null && citizens.getAsBoolean()) {
				recycleStuck(entry.getKey(), group, garrison, now);
			}

			if (garrison != null) {
				garrison.owed += lost;
				if (lost > 0) garrison.lastLossAt = now;
				if (citizens.getAsBoolean()) {
					waveTick(entry.getKey(), group, garrison, now);
				}
			}
			if (group.defenders.isEmpty() && (garrison == null || garrison.reserve() <= 0)) {
				entries.remove();
				continue;
			}

			Set<UUID> challengerIds = group.challengerMembers.get();
			double radiusSquared = group.targetingRadius * group.targetingRadius;
			for (TrackedDefender d : group.defenders) {
				if (garrison != null && leash(d.npc, garrison)) continue;
				if (challengerIds == null || challengerIds.isEmpty()) continue;
				retarget(d.npc, challengerIds, radiusSquared, garrison);
			}
		}
	}

	/** Spawns the next wave when one is due, at the hidden post farthest from the attackers. */
	private void waveTick(int turfId, Group group, TurfGarrison garrison, long now) {
		TurfDefenderConfig config = garrison.config;
		int toSpawn = TurfGarrison.toSpawn(garrison.reserve(), group.defenders.size(), config.maxAlive(),
		                                   config.waveSize(), garrison.owed, garrison.lastLossAt, now,
		                                   config.waveCooldownSeconds() * 1000L);
		if (toSpawn <= 0) return;

		World world = Bukkit.getWorld(garrison.region.getWorld());
		if (world == null) return;
		if (garrison.posts == null) {
			garrison.posts = GarrisonPosts.posts(garrison.region, world::getHighestBlockYAt);
		}
		Choice choice = GarrisonPosts.chooseSpawn(garrison.posts, attackerEyes(world, group, garrison), (post, eye) -> sees(world, post, eye));
		if (!choice.hidden()) {
			if (garrison.delayedSince < 0) {
				garrison.delayedSince = now;
			}
			if (now - garrison.delayedSince < VISIBLE_DELAY_MS) return;
		}
		garrison.delayedSince = -1L;

		Post     post     = choice.post();
		Location location = new Location(world, post.x(), post.y(), post.z());
		int      spawned  = 0;
		for (int i = 0; i < toSpawn; i++) {
			CivilianNpc npc = spawnManager.spawnCivilian(location, config.typeId());
			if (npc == null) {
				log.warn("Failed to spawn turf defender of type '{}' at {} (CivilianSpawnManager returned null)",
				         config.typeId(), location);
				// retry after the wave cooldown instead of every tick
				garrison.lastLossAt = now;
				break;
			}
			// TF-46: the reserve pays only for a guard that exists
			if (garrison.spend(1) <= 0) {
				npc.markForRemoval();
				break;
			}
			garrison.owed = Math.max(0, garrison.owed - 1);
			dress(npc, garrison);
			track(turfId, npc, now, group.lifespan);
			spawned++;
		}
		if (spawned > 0) {
			garrison.waveNotice.accept(spawned);
		}
	}

	/**
	 * A guard that found no way to anyone for {@code Recycle_Seconds}, with no attacker looking at it, is replaced by a
	 * fresh one at another post: same callsign number and role, same expiry, no stock spent. The replacement is spawned
	 * first, so a failed spawn leaves the old guard where it is until the next window.
	 */
	private void recycleStuck(int turfId, Group group, TurfGarrison garrison, long now) {
		int seconds = garrison.config.recycleSeconds();
		if (seconds <= 0 || garrison.posts == null || garrison.posts.isEmpty()) return;

		World          world = null;
		List<double[]> eyes  = List.of();
		for (TrackedDefender d : new ArrayList<>(group.defenders)) {
			LivingEntity body = d.npc.getEntity();
			if (body == null || !d.npc.isValid() || d.npc.isMarkedForRemoval()) continue;
			long stuck = d.npc.millisUnreachable();
			if (stuck <= seconds * 1000L) continue;

			if (world == null) {
				world = body.getWorld();
				eyes  = attackerEyes(world, group, garrison);
			}
			World  guardWorld = world;
			Location at       = body.getLocation();
			boolean seen = false;
			Location head = body.getEyeLocation();
			for (double[] eye : eyes) {
				if (clearLine(guardWorld, eye, head.getX(), head.getY(), head.getZ())) {
					seen = true;
					break;
				}
			}
			long last = recycledAt.getOrDefault(d.npc, -1L);
			if (!GuardRecycle.due(stuck, seconds, last, now, seen)) continue;

			Post post = GuardRecycle.elsewhere(garrison.posts, at.getX(), at.getZ(), eyes,
			                                   (p, eye) -> sees(guardWorld, p, eye));
			CivilianNpc fresh = post == null ? null : spawnManager.spawnCivilian(
					new Location(guardWorld, post.x(), post.y(), post.z()), garrison.config.typeId());
			if (fresh == null) {
				recycledAt.put(d.npc, now);
				continue;
			}
			int number = numberByNpc.getOrDefault(d.npc, 0);
			d.npc.markForRemoval();
			untrack(d);
			group.defenders.remove(d);
			group.deployed--;
			dressAs(fresh, garrison, number);
			track(turfId, fresh, d.deployedAt(), d.lifespanSeconds());
			recycledAt.put(fresh, now);
			log.debug("Turf {}: stuck guard {} recycled at another post", turfId, number);
		}
	}

	/** Gives a fresh guard its turf squad and its gang-tagged callsign. */
	private void dress(CivilianNpc npc, TurfGarrison garrison) {
		dressAs(npc, garrison, garrison.nextNumber++);
	}

	private void dressAs(CivilianNpc npc, TurfGarrison garrison, int number) {
		numberByNpc.put(npc, number);
		npc.setFactionSquads(garrison.squads);
		applyComposition(npc, number, garrison);
		CitizensBridge.callsign(npc, "&6" + GuardCallsign.format(garrison.tag, number));
	}

	/**
	 * The role guard {@code number} takes from {@code Garrison.Composition}, out of the guard type's own
	 * {@code AI.Combat.Roles}; a guard past the list, or naming an unknown role, stays plain.
	 */
	private void applyComposition(CivilianNpc npc, int number, TurfGarrison garrison) {
		TurfDefenderConfig config = garrison.config;
		if (config.composition().slots().isEmpty()) return;
		NpcRole role = config.composition().assign(number, npc.getTypeConfig().ai().roles(), unknownRoles, log::warn);
		if (role != null) npc.applyRole(role);
	}

	/** Eye positions {x, y, z} of the live attackers in {@code world}. */
	private List<double[]> attackerEyes(World world, Group group, TurfGarrison garrison) {
		Set<UUID> ids = group.challengerMembers.get();
		if (ids == null || ids.isEmpty()) return List.of();
		List<double[]> eyes = new ArrayList<>();
		for (Player player : world.getPlayers()) {
			if (!ids.contains(player.getUniqueId()) || player.isDead()) continue;
			Location eye = player.getEyeLocation();
			// far players are no threat and a long ray would load chunks on the main thread
			if (!GarrisonPosts.withinLeash(eye.getX(), eye.getZ(), garrison.region, garrison.config.leashMargin())) continue;
			eyes.add(new double[]{eye.getX(), eye.getY(), eye.getZ()});
		}
		return eyes;
	}

	/** Whether an attacker's eye has a clear line to a post (no block in between). */
	private static boolean sees(World world, Post post, double[] eye) {
		return clearLine(world, eye, post.x(), post.y() + EYE_HEIGHT, post.z());
	}

	/** Whether no block stands between an eye {x, y, z} and a point. */
	private static boolean clearLine(World world, double[] eye, double x, double y, double z) {
		Vector from = new Vector(eye[0], eye[1], eye[2]);
		Vector to   = new Vector(x, y, z);
		Vector way  = to.clone().subtract(from);
		double distance = way.length();
		if (distance < 1.0e-6) return true;
		RayTraceResult hit = world.rayTraceBlocks(from.toLocation(world), way.normalize(), distance,
		                                          FluidCollisionMode.NEVER, true);
		return hit == null;
	}

	/**
	 * Walks a guard that strayed past the leash back to the nearest post. Returns true when the guard is outside the
	 * leash (and so must not pick a new target this tick).
	 */
	private boolean leash(CivilianNpc npc, TurfGarrison garrison) {
		LivingEntity entity = npc.getEntity();
		if (entity == null || garrison.posts == null) return false;
		Location at = entity.getLocation();
		if (GarrisonPosts.withinLeash(at.getX(), at.getZ(), garrison.region, garrison.config.leashMargin())) {
			return false;
		}
		npc.setTargetPlayerId(null);
		if (npc.getCurrentState() == CivilianState.COMBAT) {
			npc.transitionTo(CivilianState.IDLE);
		}
		walkToPost(npc, at, garrison);
		return true;
	}

	/** A survivor of a finished contest: no target, back to the nearest post. */
	private void standDownOne(CivilianNpc npc, TurfGarrison garrison) {
		LivingEntity entity = npc.getEntity();
		if (entity == null) return;
		npc.setTargetPlayerId(null);
		if (npc.getCurrentState() == CivilianState.COMBAT) {
			npc.transitionTo(CivilianState.IDLE);
		}
		walkToPost(npc, entity.getLocation(), garrison);
	}

	private void walkToPost(CivilianNpc npc, Location at, TurfGarrison garrison) {
		if (garrison.posts == null) return;
		Post post = GarrisonPosts.nearest(garrison.posts, at.getX(), at.getZ());
		if (post == null || at.getWorld() == null) return;
		Location target = new Location(at.getWorld(), post.x(), post.y(), post.z());
		if (at.distanceSquared(target) > AT_POST_SQUARED) {
			npc.navigateTo(target);
		}
	}

	/** Ends a group outside the tick loop and hands its survivors back. */
	private void finish(int turfId, Group group) {
		byTurfId.remove(turfId);
		release(group);
	}

	private void release(Group group) {
		int survivors = 0;
		for (TrackedDefender d : group.defenders) {
			if (d.npc.isValid() && !d.npc.isMarkedForRemoval()) survivors++;
			untrack(d);
		}
		group.recallAll();
		TurfGarrison garrison = group.garrison;
		if (garrison != null && garrison.onReturn != null && survivors > 0) {
			garrison.onReturn.accept(survivors);
		}
	}

	private void retarget(CivilianNpc npc, Set<UUID> challengerIds, double radiusSquared, TurfGarrison garrison) {
		if (npc.getEntity() == null) return;
		Player closest     = null;
		double closestDist = radiusSquared;
		for (Player player : npc.getEntity().getWorld().getPlayers()) {
			if (!challengerIds.contains(player.getUniqueId())) continue;
			if (player.isDead()) continue;
			Location at = player.getLocation();
			if (garrison != null && !GarrisonPosts.withinLeash(at.getX(), at.getZ(), garrison.region,
			                                                   garrison.config.leashMargin())) {
				continue;
			}
			double dist = at.distanceSquared(npc.getEntity().getLocation());
			if (dist < closestDist) {
				closest     = player;
				closestDist = dist;
			}
		}
		if (closest != null) {
			npc.setTargetPlayerId(closest.getUniqueId());
			if (npc.getCurrentState() != CivilianState.COMBAT) {
				npc.transitionTo(CivilianState.COMBAT);
			}
		}
	}

	private static final class Group {

		final List<TrackedDefender> defenders = new ArrayList<>();
		int deployed;
		Supplier<Set<UUID>> challengerMembers;
		double              targetingRadius;
		/** Set for a garrison group; null for a group built by {@link #deploy}. */
		TurfGarrison        garrison;
		IntSupplier         lifespan = () -> 600;

		Group(Supplier<Set<UUID>> challengerMembers, double targetingRadius) {
			this.challengerMembers = challengerMembers;
			this.targetingRadius   = targetingRadius;
		}

		void recallAll() {
			for (TrackedDefender d : defenders) {
				try {
					if (d.npc.isValid()) d.npc.markForRemoval();
				} catch (Exception ignored) {
				}
			}
			defenders.clear();
		}
	}

	private record TrackedDefender(CivilianNpc npc, long deployedAt, IntSupplier lifespanSeconds, UUID entityId) {

		long expiresAt() {
			return deployedAt + lifespanSeconds.getAsInt() * 1000L;
		}
	}

	/**
	 * Citizens-typed calls live on a nested class so no Citizens type appears on the deployer's own signatures (the
	 * deployer is a bean). The Citizens name stays the type's plain display name (16 characters or fewer, no colour, so it never
	 * becomes a {@code CIT-} team name); the nameplate is hidden and the coloured callsign is hologram line 0, where
	 * the healthbars module puts its bar directly under it.
	 */
	private static final class CitizensBridge {

		static void callsign(CivilianNpc npc, String hologramLine) {
			NPC citizensNpc = npc.getNpc();
			if (citizensNpc == null) return;
			// no setName here: renaming a spawned PLAYER NPC respawns it and loses its mark, gear and health bonus
			citizensNpc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, false);
			citizensNpc.getOrAddTrait(HologramTrait.class).addLine(ChatUtil.color(hologramLine));
		}
	}
}
