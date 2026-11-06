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
import org.luckyraven.gangland.turf.powerups.GuardBuffs;
import org.luckyraven.gangland.npc.NpcRole;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.npc.NpcPost;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.keystone.util.ChatUtil;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * Stands and ticks a turf's guards by reusing the existing civilian NPC infrastructure. No bespoke entity types: every
 * guard is a {@link CivilianNpc} of the configured type id from {@code civilians.yml}. The civilian system handles
 * pathing, attack cadence and entity cleanup; this deployer owns the spawn, target-rebind and recall lifecycle of a
 * turf.
 *
 * <p>One {@link Group} per turf, and one loop: every 5 ticks it reconciles every stocked turf. {@code
 * turf_garrison.count} means guards owned, a body exists only in a loaded chunk, and only a contest kill debits ({@link
 * #markKilled}). A guard that vanishes (chunk unload, reload) costs nothing and its slot refills. Outside a contest
 * guards stand one per post, up to {@code Standing_Cap}, and attack nobody unprovoked. {@link #startGarrison} turns the
 * same bodies into a contest: they top up to {@code Max_Alive} in waves at the hidden post farthest from the attackers,
 * keep to a leash of the turf plus a margin and target the closest live challenger. {@link #standDown} ends it: they
 * shout and walk home, and guards past the peace target leave after {@code Stand_Down_Seconds}.
 *
 * <p>ponytail: no global NPC budget. Load is at most {@code Standing_Cap} per turf with a loaded spot; add one when
 * many owned turfs are loaded at once and it shows. No per-group catch either, so a bug in one group's tick stays loud.
 */
@CustomLog
public final class TurfDefenderDeployer implements BeanLifecycle {

	private static final long TICK_PERIOD = 5L;
	/** The same wait as the Quartermaster's first spawn: worlds and Citizens are online by then. */
	private static final long FIRST_TICK_DELAY = 60L;
	/** How long a wave waits for a post no attacker can see before it uses the farthest one anyway. */
	private static final long VISIBLE_DELAY_MS = 3_000L;
	private static final double EYE_HEIGHT = 1.6;
	private static final double AT_POST_SQUARED = 4.0;

	private GarrisonRoster             roster;
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
	private       IntPredicate        holding  = turfId -> false;

	public TurfDefenderDeployer(JavaPlugin plugin,
	                            CivilianService civilianService,
	                            CivilianSpawnManager spawnManager) {
		this.plugin          = plugin;
		this.civilianService = civilianService;
		this.spawnManager    = spawnManager;
	}

	public void start() {
		if (tickTask != null) return;
		tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, FIRST_TICK_DELAY, TICK_PERIOD);
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

	/** The source of every stocked turf's garrison, config and Toughness; the tick reads it live. */
	public void bindRoster(GarrisonRoster roster) {
		this.roster = roster;
	}

	/** W7-F: whether a turf's guards are on the Hold order; unbound, every turf defends. */
	public void bindHolding(IntPredicate holding) {
		this.holding = holding;
	}

	/** Test seam: whether Citizens counts as available. */
	void setCitizensAvailable(BooleanSupplier available) {
		this.citizens = available;
	}

	/**
	 * A contest starts on this turf. The guards already standing keep standing and nothing respawns; the tick tops them
	 * up to {@code Max_Alive}. A restart during the stand-down window is this same path (T-751). Without Citizens
	 * nothing is armed (TF-46).
	 *
	 * @param challengers the players the guards fight, re-read on every tick
	 */
	public void startGarrison(TurfGarrison garrison, Supplier<Set<UUID>> challengers) {
		if (!citizens.getAsBoolean()) return;

		Group group = byTurfId.get(garrison.turfId);
		if (group == null || group.garrison == null) {
			recall(garrison.turfId);
			group = newGroup(garrison);
		} else {
			// T-767: live tag, config, owner and price
			group.garrison.adopt(garrison);
			group.garrison.squads = new TurfGarrisonSquads(civilianService, garrison.turfId, garrison.tag);
			// a rally only pulls guards holding the same instance
			for (TrackedDefender d : group.defenders) d.npc.setFactionSquads(group.garrison.squads);
		}
		group.challengerMembers       = challengers;
		group.contested               = true;
		group.garrison.standingDown   = false;
		// a contest that ended during the visibility wait must not skip this one's
		group.garrison.delayedSince   = -1L;
		// T-281, T-285: "half the guards" counts from here
		group.deployed                = aliveCount(garrison.turfId);
	}

	private Group newGroup(TurfGarrison garrison) {
		garrison.squads = new TurfGarrisonSquads(civilianService, garrison.turfId, garrison.tag);
		Group group = new Group(Set::of, garrison.config.targetingRadius());
		group.garrison = garrison;
		byTurfId.put(garrison.turfId, group);
		return group;
	}

	/**
	 * The contest ended. The guards shout {@code Turf_Held} or {@code Turf_Lost}, drop their targets and walk home;
	 * after {@code Stand_Down_Seconds} those past the peace target leave. The stock does not change.
	 */
	public void standDown(int turfId, boolean held) {
		Group group = byTurfId.get(turfId);
		if (group == null) return;
		TurfGarrison garrison = group.garrison;
		if (garrison == null) {
			recall(turfId);
			return;
		}
		if (garrison.standingDown) return;

		group.contested         = false;
		group.challengerMembers = Set::of;
		garrison.standingDown   = true;
		garrison.standDownAt    = System.currentTimeMillis() + garrison.config.standDownSeconds() * 1000L;
		String line = held ? "Turf_Held" : "Turf_Lost";
		for (TrackedDefender d : group.defenders) {
			if (!d.npc.isValid() || d.npc.isMarkedForRemoval()) continue;
			standDownOne(d.npc, garrison);
			civilianService.shout(d.npc, line, Map.of());
		}
	}

	/**
	 * Recall every guard of {@code turfId}: an owner change or a delete. The stock is the caller's business.
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

	/**
	 * T-251: records that this guard died (not merely vanished); a no-op for an NPC that is not a tracked guard. This
	 * is the only debit, decided at the moment of death: a guard killed in a contest leaves the stock, whoever or
	 * whatever killed it, and one killed with no contest running is free (ruling 1).
	 */
	public void markKilled(CivilianNpc npc) {
		Integer turfId = turfByNpc.get(npc);
		if (turfId == null || !killed.add(npc)) return;
		Group group = byTurfId.get(turfId);
		if (group != null && group.contested && group.garrison != null) {
			group.garrison.spend(1);
		}
	}

	/** What a guard's death pays a rival: the gang that paid for it and the cash. */
	public record GuardBounty(int ownerGangId, int amount) {
	}

	/**
	 * What killing this guard pays a rival: {@code floor(price x Bounty_Share)} of what the owner paid for it, or
	 * {@code null} for an NPC that is not a tracked guard, or with no contest running. It reads the same flag as the
	 * debit in the same event, so a bounty never pays without a debit. The owner funded it already, so nothing is
	 * minted.
	 */
	@Nullable
	public GuardBounty bountyOf(CivilianNpc npc) {
		Integer turfId = turfByNpc.get(npc);
		Group   group  = turfId == null ? null : byTurfId.get(turfId);
		if (group == null || group.garrison == null || !group.contested) return null;
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

	/** Turf of a tracked guard by NPC, or -1; unlike {@link #findOwningTurfId} it needs no live body. */
	public int turfOf(CivilianNpc npc) {
		return turfByNpc.getOrDefault(npc, -1);
	}

	/**
	 * The guards of {@code turfId} that are still alive; empty when the turf has no group.
	 */
	public List<CivilianNpc> liveDefenders(int turfId) {
		Group group = byTurfId.get(turfId);
		if (group == null) return List.of();
		List<CivilianNpc> live = new ArrayList<>(group.defenders.size());
		for (TrackedDefender d : group.defenders) {
			if (d.npc.isValid() && !d.npc.isMarkedForRemoval()) live.add(d.npc);
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

	/** How many guards of {@code turfId} are alive and valid right now (0 when none stand). */
	public int aliveCount(int turfId) {
		return liveDefenders(turfId).size();
	}

	/**
	 * Adds one guard to its turf's group and the lookup indexes, and gives it the running Toughness. Every spawn path
	 * comes here after {@code dressAs}, so the role has already set its health and that becomes the guard's base;
	 * rescaling from the stored base means a stack and an expiry never drift. Package-private for tests.
	 */
	void track(int turfId, CivilianNpc npc) {
		Group group = byTurfId.computeIfAbsent(turfId, k -> new Group(Set::of, 32.0));
		LivingEntity entity = npc.getEntity();
		UUID entityId = entity == null ? null : entity.getUniqueId();
		double baseHealth = entity == null ? -1 : GuardBuffs.maxHealth(entity);
		if (baseHealth > 0 && group.toughness != 1.0) {
			GuardBuffs.applyHealth(entity, baseHealth, group.toughness);
		}
		group.defenders.add(new TrackedDefender(npc, entityId, baseHealth));
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
		// before the iterator: building a group inside it throws a CME
		reconcile();

		for (Iterator<Map.Entry<Integer, Group>> entries = byTurfId.entrySet().iterator(); entries.hasNext(); ) {
			Map.Entry<Integer, Group> entry    = entries.next();
			int                       turfId   = entry.getKey();
			Group                     group    = entry.getValue();
			TurfGarrison              garrison = group.garrison;

			if (garrison != null && roster != null) {
				// live, so a reload and the buffs apply (T-767, TF-36)
				garrison.config       = roster.configOf(turfId);
				group.targetingRadius = garrison.config.targetingRadius();
				applyToughness(group, roster.guardHealth(turfId));
			}

			reap(turfId, group, garrison, now);

			// Nothing leaves during a contest, nor during the stand-down delay unless there is no stock left to stand
			// on.
			if (garrison != null && !group.contested && (!garrison.standingDown || garrison.owned() <= 0)) {
				trim(group, TurfGarrison.target(garrison.owned(), false, garrison.config));
			}

			if (garrison != null && garrison.standingDown) {
				if (now < garrison.standDownAt && garrison.owned() > 0) {
					for (TrackedDefender d : group.defenders) {
						standDownOne(d.npc, garrison);
					}
					continue;
				}
				garrison.standingDown = false;
			}

			if (garrison != null && group.contested && citizens.getAsBoolean()) {
				recycleStuck(turfId, group, garrison, now);
			}
			if (garrison != null && citizens.getAsBoolean()) {
				waveTick(turfId, group, garrison, now);
			}
			if (group.defenders.isEmpty() && (garrison == null || garrison.owned() <= 0) && !group.contested) {
				entries.remove();
				continue;
			}

			Set<UUID> challengerIds = group.challengerMembers.get();
			double radiusSquared = group.targetingRadius * group.targetingRadius;
			boolean hold = garrison != null && holding.test(turfId);
			for (TrackedDefender d : group.defenders) {
				if (garrison != null && leash(d.npc, garrison)) continue;
				if (hold && holdToPost(d.npc, garrison)) continue;
				if (garrison != null && !group.contested) {
					goHome(d.npc, garrison);
					continue;
				}
				if (challengerIds == null || challengerIds.isEmpty()) continue;
				retarget(d.npc, challengerIds, radiusSquared, garrison, hold);
			}
		}
	}

	/** Builds a group for every stocked, owned turf that has none. Without Citizens nothing stands. */
	private void reconcile() {
		if (roster == null || !citizens.getAsBoolean()) return;
		for (int turfId : roster.stockedTurfIds()) {
			if (byTurfId.containsKey(turfId)) continue;
			TurfGarrison garrison = roster.garrisonOf(turfId);
			if (garrison != null) {
				newGroup(garrison);
			}
		}
	}

	/** Ruling 5: a Toughness buff started, stacked or ended, so every live guard moves from its own base health. */
	private void applyToughness(Group group, double multiplier) {
		if (multiplier == group.toughness) return;
		group.toughness = multiplier;
		for (TrackedDefender d : group.defenders) {
			LivingEntity body = d.npc.getEntity();
			if (d.baseHealth > 0 && body != null && d.npc.isValid()) {
				GuardBuffs.applyHealth(body, d.baseHealth, multiplier);
			}
		}
	}

	/**
	 * Drops the guards that are gone. Only one the death listener reported counts as down (T-251) and starts the
	 * cooldown; one that vanished costs nothing (I5). T-882: {@code markForRemoval} is unconditional, because a chunk
	 * reloading before {@code CivilianService} destroys an invalid guard would otherwise leave a valid, untracked body
	 * beside its replacement.
	 */
	private void reap(int turfId, Group group, @Nullable TurfGarrison garrison, long now) {
		int down = 0;
		for (Iterator<TrackedDefender> it = group.defenders.iterator(); it.hasNext(); ) {
			TrackedDefender d = it.next();
			if (d.npc.isValid() && !d.npc.isMarkedForRemoval()) continue;
			if (killed.contains(d.npc)) {
				down++;
			} else {
				log.debug("Turf {}: guard left without dying (despawned or unloaded); not counted as down", turfId);
			}
			d.npc.markForRemoval();
			untrack(d);
			it.remove();
		}
		if (down <= 0) return;
		if (garrison != null) {
			garrison.lastLossAt = now;
		}
		// the half-guards alert belongs to a contest; owners hear of a peace kill through Alerts.Guard_Down
		if (garrison == null || group.contested) {
			Bukkit.getPluginManager().callEvent(
					new TurfDefenderDownEvent(turfId, group.defenders.size(), group.deployed));
		}
	}

	/**
	 * Removes guards past {@code target}, and any numbered above it, the highest numbers first, with no debit. Leaves
	 * the list at once. A number above the target shares a low number's post, so it gives way and the free low number
	 * refills its own post (ruling 2).
	 */
	private void trim(Group group, int target) {
		while (!group.defenders.isEmpty()) {
			TrackedDefender surplus = Collections.max(group.defenders, Comparator.comparingInt(d -> numberOf(d.npc)));
			if (group.defenders.size() <= target && numberOf(surplus.npc) <= target) return;
			// ponytail: the body lives until CivilianService's next tick (AI_Tick_Rate) but is untracked now, so a killing
			// blow there drops civilian cash with no debit (T-901); hold the entity mapping until it is gone if that matters
			surplus.npc.markForRemoval();
			untrack(surplus);
			group.defenders.remove(surplus);
		}
	}

	private int numberOf(CivilianNpc npc) {
		return numberByNpc.getOrDefault(npc, 0);
	}

	/** The callsign numbers of the group's live guards. */
	private Set<Integer> numbersOf(Group group) {
		Set<Integer> numbers = new HashSet<>();
		for (TrackedDefender d : group.defenders) {
			numbers.add(numberOf(d.npc));
		}
		return numbers;
	}

	/**
	 * Spawns toward the target when guards are due, with no debit: one per post outside a contest, at the hidden post
	 * farthest from the attackers in one. The height is read only for a loaded chunk, so this loop never loads one.
	 */
	private void waveTick(int turfId, Group group, TurfGarrison garrison, long now) {
		TurfDefenderConfig config = garrison.config;
		int stock   = garrison.owned();
		int alive   = group.defenders.size();
		int target  = TurfGarrison.target(stock, group.contested, config);
		int toSpawn = TurfGarrison.toSpawn(stock - alive, alive, target, config.waveSize(), target - alive,
		                                   garrison.lastLossAt, now, config.waveCooldownSeconds() * 1000L);
		if (toSpawn <= 0) return;

		World world = Bukkit.getWorld(garrison.region.getWorld());
		if (world == null) return;
		List<Post> spots = GarrisonPosts.loaded(garrison.region, world::isChunkLoaded, world::getHighestBlockYAt);
		garrison.remember(spots);

		int spawned = group.contested
		              ? spawnContest(turfId, group, garrison, world, spots, toSpawn, now)
		              : spawnStanding(turfId, group, garrison, world, spots, target, toSpawn, now);
		if (spawned > 0 && group.contested) {
			garrison.waveNotice.accept(spawned);
		}
	}

	/** Walks numbers 1 to {@code target}, skipping one a live guard holds or whose spot is not loaded (T-298). */
	private int spawnStanding(int turfId, Group group, TurfGarrison garrison, World world, List<Post> spots, int target,
	                          int toSpawn, long now) {
		Set<Integer> taken   = numbersOf(group);
		int          spawned = 0;
		for (int number = 1; number <= target && spawned < toSpawn; number++) {
			if (taken.contains(number)) continue;
			// ponytail: a post over lava or in water can kill a guard repeatedly; free in peace, and the 60 s alert
			// tells the owner
			Post post = spots.get((number - 1) % spots.size());
			if (post == null) continue;
			if (!spawnAt(turfId, group, garrison, world, post, number, now)) break;
			spawned++;
		}
		return spawned;
	}

	/** Each guard takes the lowest free number. A batch may still share a post (T-298 stays open for this case). */
	private int spawnContest(int turfId, Group group, TurfGarrison garrison, World world, List<Post> spots, int toSpawn,
	                         long now) {
		List<Post> posts = new ArrayList<>(spots.size());
		for (Post post : spots) {
			if (post != null) posts.add(post);
		}
		// chooseSpawn throws on an empty list, and that would stall every later turf in this tick
		if (posts.isEmpty()) return 0;

		Choice choice = GarrisonPosts.chooseSpawn(posts, attackerEyes(world, group, garrison),
		                                          (post, eye) -> sees(world, post, eye));
		if (!choice.hidden()) {
			if (garrison.delayedSince < 0) {
				garrison.delayedSince = now;
			}
			if (now - garrison.delayedSince < VISIBLE_DELAY_MS) return 0;
		}
		garrison.delayedSince = -1L;

		int spawned = 0;
		for (int i = 0; i < toSpawn; i++) {
			int number = TurfGarrison.lowestFree(numbersOf(group));
			if (!spawnAt(turfId, group, garrison, world, choice.post(), number, now)) break;
			spawned++;
		}
		return spawned;
	}

	/** One guard at {@code post} with {@code number}. A failed spawn retries after the cooldown, not every tick. */
	private boolean spawnAt(int turfId, Group group, TurfGarrison garrison, World world, Post post, int number,
	                        long now) {
		Location    location = new Location(world, post.x(), post.y(), post.z());
		CivilianNpc npc      = spawnManager.spawnCivilian(location, garrison.config.typeId());
		if (npc == null) {
			log.warn("Failed to spawn turf defender of type '{}' at {} (CivilianSpawnManager returned null)",
			         garrison.config.typeId(), location);
			garrison.lastLossAt = now;
			return false;
		}
		dressAs(npc, garrison, number);
		track(turfId, npc);
		return true;
	}

	/**
	 * A guard that found no way to anyone for {@code Recycle_Seconds}, with no attacker looking at it, is replaced by a
	 * fresh one at another post: same callsign number and role, no stock spent. The replacement is spawned
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
			track(turfId, fresh);
			recycledAt.put(fresh, now);
			log.debug("Turf {}: stuck guard {} recycled at another post", turfId, number);
		}
	}

	/** Gives a fresh guard its turf squad, its role and its gang-tagged callsign. */
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
	 * Walks a guard that strayed past the leash back to its post. Returns true when the guard is outside the
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

	/**
	 * Hold order (W7-F): a guard further than the hold radius from its own post drops its target and walks back.
	 * Returns true when it did (the guard must not pick a new target this tick).
	 */
	private boolean holdToPost(CivilianNpc npc, TurfGarrison garrison) {
		LivingEntity entity = npc.getEntity();
		if (entity == null || garrison.posts == null) return false;
		Location at   = entity.getLocation();
		Post     post = homeOf(npc, garrison, at);
		if (post == null || GarrisonPosts.withinHold(post, at.getX(), at.getZ(), garrison.config.holdRadius())) {
			return false;
		}
		npc.setTargetPlayerId(null);
		if (npc.getCurrentState() == CivilianState.COMBAT) {
			npc.transitionTo(CivilianState.IDLE);
		}
		walkToPost(npc, at, garrison);
		return true;
	}

	/**
	 * A survivor of a finished contest: no target, back to its post. Both targets go, because bodies stay after a
	 * contest and {@code CivilianIdleBehavior} would re-engage a remembered player or entity. This runs on every tick
	 * of the stand-down window, so guards answer a cop again only on his first hit after it.
	 */
	private void standDownOne(CivilianNpc npc, TurfGarrison garrison) {
		LivingEntity entity = npc.getEntity();
		if (entity == null) return;
		npc.setTargetPlayerId(null);
		npc.setTargetEntity(null);
		if (npc.getCurrentState() == CivilianState.COMBAT) {
			npc.transitionTo(CivilianState.IDLE);
		}
		walkToPost(npc, entity.getLocation(), garrison);
	}

	private void walkToPost(CivilianNpc npc, Location at, TurfGarrison garrison) {
		Post post = homeOf(npc, garrison, at);
		if (post == null || at.getWorld() == null) return;
		Location target = new Location(at.getWorld(), post.x(), post.y(), post.z());
		if (at.distanceSquared(target) > AT_POST_SQUARED) {
			npc.navigateTo(target);
		}
	}

	/** The spot of the guard's number, else the nearest known post (a guard with no number, the test seam). */
	@Nullable
	private Post homeOf(CivilianNpc npc, TurfGarrison garrison, Location at) {
		Post home = garrison.homeOf(numberOf(npc));
		if (home != null || garrison.posts == null) return home;
		return GarrisonPosts.nearest(garrison.posts, at.getX(), at.getZ());
	}

	/**
	 * Idle behaviour outside a contest. A guard in COMBAT (it was hit, or it answers a cop) is left to the fight;
	 * afterwards it walks back to its own post. The distance check comes first because {@code holdPost} always
	 * navigates: without it a guard at home would hold, arrive and release every 5 ticks.
	 *
	 * <p>ponytail: a rival's hit or an answer to a cop sets {@code wantedByPolice} ({@code CivilianNpc}) and free cops
	 * can come for the guard with no range limit in the same world. Under free peace deaths this costs no stock; watch
	 * it on the Test Server (T-885, T-891). A post on an unreachable roof re-paths whenever the guard stops, bounded by
	 * 5 guards per loaded turf.
	 */
	private void goHome(CivilianNpc npc, TurfGarrison garrison) {
		if (npc.getCurrentState() == CivilianState.COMBAT) {
			if (npc.getPost() != null) npc.releasePost();
			return;
		}
		// an admin who points Type_Id at a wandering type; the shipped type has Wander off
		if (npc.getCurrentState() == CivilianState.WANDERING) npc.transitionTo(CivilianState.IDLE);
		LivingEntity body = npc.getEntity();
		if (body == null) return;
		Location at   = body.getLocation();
		Post     home = homeOf(npc, garrison, at);
		if (home == null) return;
		if (npc.getPost() == null) {
			double dx = home.x() - at.getX();
			double dz = home.z() - at.getZ();
			if (dx * dx + dz * dz <= AT_POST_SQUARED) return;
			Location anchor = new Location(at.getWorld(), home.x(), home.y(), home.z());
			npc.holdPost(new NpcPost(anchor, NpcPost.ARRIVE_RADIUS, null));
		}
		if (npc.tickPost()) npc.releasePost();
	}

	private void retarget(CivilianNpc npc, Set<UUID> challengerIds, double radiusSquared, TurfGarrison garrison,
	                      boolean hold) {
		if (npc.getEntity() == null) return;
		// Hold: only attackers near this guard's own post are fair game
		Post holdPost = hold ? homeOf(npc, garrison, npc.getEntity().getLocation()) : null;
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
			if (holdPost != null && !GarrisonPosts.withinHold(holdPost, at.getX(), at.getZ(),
			                                                  garrison.config.holdRadius())) {
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
		/** Set for a garrison group; null only for a group the {@link #track} test seam built. */
		TurfGarrison        garrison;
		/** A contest is running: the only state in which a death debits and a bounty pays. */
		boolean             contested;
		/** The Toughness multiplier the live guards were last scaled to. */
		double              toughness = 1.0;

		Group(Supplier<Set<UUID>> challengerMembers, double targetingRadius) {
			this.challengerMembers = challengerMembers;
			this.targetingRadius   = targetingRadius;
		}

		void recallAll() {
			for (TrackedDefender d : defenders) {
				try {
					// T-882: unconditional, as in the reap
					d.npc.markForRemoval();
				} catch (Exception ignored) {
				}
			}
			defenders.clear();
		}
	}

	/** @param baseHealth the guard's role health, what Toughness scales from; -1 with no attribute (never rescaled) */
	private record TrackedDefender(CivilianNpc npc, UUID entityId, double baseHealth) {
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
