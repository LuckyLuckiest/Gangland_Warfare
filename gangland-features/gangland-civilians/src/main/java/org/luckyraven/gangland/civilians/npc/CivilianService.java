package org.luckyraven.gangland.civilians.npc;

import lombok.CustomLog;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.civilians.message.CivilianMessages;
import org.luckyraven.gangland.civilians.npc.config.*;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpcFactory;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawner;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.gangland.npc.radio.RadioVoice;
import org.luckyraven.gangland.npc.radio.SquadRadio;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.npc.AbstractNpc;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.NpcSquadSignal;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.keystone.npc.spi.NpcSquadListener;
import org.luckyraven.keystone.timer.RepeatingTimer;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages the lifecycle and AI ticking of all active civilian NPCs.
 * <p>
 * Runtime setup (config reading, timer start) happens in {@link #onInitialize(boolean)}, called by the bean lifecycle
 * after all beans are constructed.
 * <p>
 * Also resolves each faction's shared combat squad ({@link FactionSquads}, phase H12): a squad's listener speaks its
 * {@link NpcSquadSignal}s as faction shouts ({@link SquadRadio}) and, on a {@link NpcSquadSignal#CONTACT} edge, queues
 * the spotter's nearby allies to be recruited by shout — beyond {@code Combat.Alert_Range}, out to
 * {@code Shouts.Range} — once the current NPC tick loop finishes.
 */
@CustomLog
public class CivilianService implements BeanLifecycle, FactionSquads {

	private final JavaPlugin           plugin;
	private final CiviliansLoader      civiliansLoader;
	private final NpcMarkManager       markManager;
	private final CivilianSettings     civilianSettings;
	private final CivilianNpcFactory   npcFactory;
	private final CivilianSpawnManager spawnManager;
	private final CivilianNpcRegistry  registry;
	private final SquadRadio           shouts;
	/** Package-private (not {@code private}) so tests can inspect what it resolves for a given squad. */
	final RadioVoice voice = new FactionVoice();

	/**
	 * Combat squads, one per faction and target (player or entity uuid): created on the first hit, dropped when empty
	 * or when their target dies or goes down.
	 */
	private final Map<SquadKey, NpcSquad> squads = new HashMap<>();
	/** Reverse index so a squad's listener (only handed the {@link NpcSquad}) can resolve its faction and target. */
	private final Map<NpcSquad, SquadKey> keyOf  = new HashMap<>();
	/**
	 * A CONTACT edge queues its spotter here rather than recruiting re-entrantly from inside the squad's listener
	 * (which must never mutate the squad it was called from); drained by {@link #tickAll} after the NPC loop.
	 */
	private final Queue<Recruit> pendingRecruits = new ArrayDeque<>();

	/**
	 * Every squad's listener: relays signals as faction shouts, then queues CONTACT recruitment. Package-private (not
	 * {@code private}) so tests can fire a signal directly. Assigned in the constructor, not here, because it closes
	 * over {@link #shouts}, itself only assigned there.
	 */
	final NpcSquadListener squadListener;

	@Getter
	CiviliansConfig civiliansConfig;

	private RepeatingTimer tickTimer;
	private RepeatingTimer checkTimer;

	public CivilianService(JavaPlugin plugin, CiviliansLoader civiliansLoader, NpcMarkManager markManager,
	                       CivilianSettings civilianSettings, CivilianNpcFactory npcFactory,
	                       CivilianSpawnManager spawnManager, CivilianNpcRegistry registry,
	                       CivilianMessages civilianMessages) {
		this.plugin           = plugin;
		this.civiliansLoader  = civiliansLoader;
		this.markManager      = markManager;
		this.civilianSettings = civilianSettings;
		this.npcFactory       = npcFactory;
		this.spawnManager     = spawnManager;
		this.registry         = registry;
		registry.factionSquads = this;
		this.shouts           = new SquadRadio(() -> civiliansConfig.shouts(), civilianMessages,
		                                       System::currentTimeMillis,
		                                       () -> ThreadLocalRandom.current().nextDouble(),
		                                       (task, delayTicks) -> plugin.getServer().getScheduler()
		                                               .runTaskLater(plugin, task, delayTicks));
		this.squadListener    = (squad, signal, member, where) -> {
			shouts.listener(voice).onSignal(squad, signal, member, where);
			if (signal == NpcSquadSignal.CONTACT && member instanceof CivilianNpc caller) {
				pendingRecruits.add(new Recruit(caller, squad));
			}
		};
	}

	// ── Lifecycle ─────────────────────────────────────────────────────────────

	@Override
	public void onInitialize(boolean firstLoad) {
		this.civiliansConfig = civiliansLoader.getLoadedConfig();

		// T-H4: every civilian spawn task goes through Citizens (CivilianNpcFactory#createCivilian calls
		// CitizensAPI.getNPCRegistry()) — never start the tick/proximity-check timers without it. CiviliansModule
		// reports npc.citizens.missing once at module-enable time; this guard is what actually prevents the tasks.
		if (!NpcSupport.available()) {
			log.debug("Citizens is not installed or not enabled — civilian NPCs will not tick.");
			return;
		}

		if (!civilianSettings.isCivilianAiEnabled()) {
			log.debug("Civilian AI is disabled — NPCs will not tick.");
			return;
		}

		int tickRate = civilianSettings.getCivilianAiTickRate();
		this.tickTimer = new RepeatingTimer(plugin, tickRate, 0, timer -> tickAll());
		tickTimer.start(false);

		int checkInterval = civilianSettings.getCivilianSpawnerCheckInterval();
		this.checkTimer = new RepeatingTimer(plugin, checkInterval, 0,
		                                     timer -> tickProximitySpawners(civilianSettings));
		checkTimer.start(false);

		log.debug("CivilianService initialized (tick rate: {} ticks, proximity check: {} ticks).", tickRate,
		          checkInterval);
	}

	@Override
	public void onPreClear() {
		if (tickTimer != null) tickTimer.stop();
		if (checkTimer != null) checkTimer.stop();
		shutdown();
	}

	@Override
	public void onClear() {
		civiliansConfig = null;
		tickTimer       = null;
		checkTimer      = null;
	}

	@Override
	public void onShutdown() {
		shutdown();
	}

	// ── Registry delegates ───────────────────────────────────────────────────

	public void register(CivilianNpc npc) {
		registry.register(npc);
	}

	@Nullable
	public CivilianNpc getNpc(UUID entityId) {
		return registry.getNpc(entityId);
	}

	public Collection<CivilianNpc> getActiveNpcs() {
		return registry.getActiveNpcs();
	}

	public Collection<CivilianGroup> getActiveGroups() {
		return registry.getActiveGroups();
	}

	// ── Combat squads ─────────────────────────────────────────────────────────

	/**
	 * A hostile civilian was hit: it joins its faction's squad against the attacker and reports where the attacker is.
	 * Every other active, combat-enabled hostile civilian of the same faction within its own
	 * {@code AI.Combat.Alert_Range} of the victim that is not already fighting someone else takes the attacker as its
	 * target, enters {@link CivilianState#COMBAT} and joins the squad. One hop: joining does not alert anyone else; a
	 * later hit on any member alerts that member's neighbours.
	 *
	 * @param victim the civilian that was hit, already targeting {@code attacker}
	 * @param attacker the player or entity that hit it
	 * @param playerAttacker whether {@code attacker} is a real player (allies target it by player id) rather than an
	 * 		NPC or mob (allies put it at the front of their entity target queue)
	 */
	public void alertFaction(CivilianNpc victim, LivingEntity attacker, boolean playerAttacker) {
		UUID     attackerId = attacker.getUniqueId();
		String   faction    = victim.getTypeConfig().faction();
		NpcSquad squad = squads.computeIfAbsent(new SquadKey(faction, attackerId),
		                                        key -> newSquad(key, victim.getTypeConfig()));

		victim.joinSquad(squad, attackerId);
		squad.reportSighting(attacker.getLocation());

		LivingEntity victimEntity = victim.getEntity();
		if (victimEntity == null) return;

		// Friendly fire: a same-faction civilian's stray hit must not rally the faction against its own member. The
		// victim still fights back (joinSquad/reportSighting above already ran) - only recruiting allies is skipped.
		CivilianNpc attackerNpc = registry.getNpc(attackerId);
		if (attackerNpc != null && attackerNpc.isValid() && faction.equals(attackerNpc.getTypeConfig().faction())) {
			return;
		}

		recruit(victim, attacker, playerAttacker, squad, 0);
	}

	/**
	 * Resolves the shared faction squad for a combat entry that did not come from a hit (turf-defender retarget, idle
	 * re-engage): one squad per faction and target, same as {@link #alertFaction}'s.
	 */
	@Override
	public NpcSquad squadFor(CivilianNpc npc, LivingEntity target) {
		SquadKey key   = new SquadKey(npc.getTypeConfig().faction(), target.getUniqueId());
		NpcSquad squad = squads.computeIfAbsent(key, k -> newSquad(k, npc.getTypeConfig()));
		if (squad.lastKnownLocation() == null) squad.reportSighting(target.getLocation());
		npc.joinSquad(squad, target.getUniqueId());
		return squad;
	}

	/**
	 * Forgets every faction's squad hunting {@code targetId} (the target died or went down).
	 */
	public void dropSquads(UUID targetId) {
		squads.entrySet().removeIf(entry -> {
			boolean match = entry.getKey().targetId().equals(targetId);
			if (match) keyOf.remove(entry.getValue());
			return match;
		});
	}

	/**
	 * A new squad for {@code key}: the type's formation arc, and {@link #squadListener} wired so its signals become
	 * faction shouts and its {@link NpcSquadSignal#CONTACT} edges queue recruitment.
	 */
	private NpcSquad newSquad(SquadKey key, CivilianTypeConfig type) {
		NpcSquad squad = new NpcSquad();
		squad.setFormationArc(type.ai().tactics().formationArc());
		squad.setListener(squadListener);
		keyOf.put(squad, key);
		return squad;
	}

	/**
	 * Pulls every active, combat-enabled hostile civilian of {@code caller}'s faction within {@code max(its own
	 * Alert_Range, minRange)} of it (that is not already fighting someone else) onto {@code attacker}, in {@code
	 * squad}. Shared by the hit path ({@link #alertFaction}, {@code minRange} 0 - unchanged) and shout-driven
	 * recruitment ({@link #drainPendingRecruits}, {@code minRange} = {@code Shouts.Range}).
	 *
	 * @return how many allies were recruited
	 */
	private int recruit(CivilianNpc caller, LivingEntity attacker, boolean playerAttacker, NpcSquad squad,
	                    double minRange) {
		LivingEntity callerEntity = caller.getEntity();
		if (callerEntity == null) return 0;

		UUID   attackerId = attacker.getUniqueId();
		String faction    = caller.getTypeConfig().faction();
		int    recruited  = 0;

		// ponytail: scans every active civilian per call; add a spatial index if active civilians reach the hundreds
		for (CivilianNpc ally : registry.getActiveNpcs()) {
			if (ally == caller || !ally.isValid() || ally.isMarkedForRemoval()) continue;
			if (ally.getEntity() != null && attackerId.equals(ally.getEntity().getUniqueId())) continue;
			if (!ally.isHostile() || !ally.getTypeConfig().ai().combatEnabled()) continue;
			if (!faction.equals(ally.getTypeConfig().faction())) continue;
			double range = Math.max(ally.getTypeConfig().ai().alertRange(), minRange);
			if (ally.distanceTo(callerEntity) > range) continue;
			if (isFightingAnother(ally, attackerId)) continue;
			if (ally.getSquad() == squad) continue; // already in this fight: not a new recruit

			if (playerAttacker) {
				ally.setTargetPlayerId(attackerId);
			} else {
				ally.addEntityTargetToFront(attacker);
			}
			ally.joinSquad(squad, attackerId);
			if (ally.getCurrentState() != CivilianState.COMBAT) {
				ally.transitionTo(CivilianState.COMBAT);
			}
			recruited++;
		}

		return recruited;
	}

	/**
	 * Recruits by shout for every {@link NpcSquadSignal#CONTACT} queued since the last drain: allies out to {@code
	 * Shouts.Range}, beyond the caller's own {@code Alert_Range} - the CONTACT edge is Keystone hearing, independent of
	 * any player in range. A "Rally" line reports the count when it recruited at least one.
	 * <p>
	 * Package-private (not {@code private}) so tests can drain a queued CONTACT without the rest of {@link #tickAll}.
	 */
	void drainPendingRecruits() {
		Recruit pending;
		while ((pending = pendingRecruits.poll()) != null) {
			NpcSquad squad = pending.squad();
			SquadKey key   = keyOf.get(squad);
			if (key == null) continue; // the squad emptied and was pruned before this drain

			LivingEntity target = entityById(key.targetId());
			if (target == null || !target.isValid()) continue; // the target is gone

			CivilianNpc targetNpc = registry.getNpc(key.targetId());
			if (targetNpc != null && key.faction().equals(targetNpc.getTypeConfig().faction())) {
				continue; // never rally a faction against its own member
			}

			// a Citizens PLAYER-typed NPC (a cop) is an entity target: Bukkit.getPlayer cannot resolve it
			boolean player = target instanceof Player p && !NpcSupport.isNpc(p);
			int     n      = recruit(pending.caller(), target, player, squad, civiliansConfig.shouts().range());
			if (n == 0) continue;

			LivingEntity callerEntity = pending.caller().getEntity();
			if (callerEntity == null) continue;
			shouts.say(squad, voice, callerEntity, voice.callsign(pending.caller()), "Rally", "Format",
			          callerEntity.getLocation(), null, Map.of("count", String.valueOf(n)));
		}
	}

	@Nullable
	private static LivingEntity entityById(UUID id) {
		Player player = Bukkit.getPlayer(id);
		if (player != null) return player;
		Entity entity = Bukkit.getEntity(id);
		return entity instanceof LivingEntity living ? living : null;
	}

	/**
	 * Whether {@code npc} is already in combat against a target other than {@code targetId}.
	 */
	private static boolean isFightingAnother(CivilianNpc npc, UUID targetId) {
		if (npc.getCurrentState() != CivilianState.COMBAT) return false;

		LivingEntity entityTarget = npc.getTargetEntity();
		UUID         current      = entityTarget != null ? entityTarget.getUniqueId() : npc.getTargetPlayerId();
		return current != null && !current.equals(targetId);
	}

	/**
	 * One faction's hunt for one target.
	 */
	private record SquadKey(String faction, UUID targetId) {
	}

	/** A queued {@link NpcSquadSignal#CONTACT}: the spotter and the squad it spotted for. */
	private record Recruit(CivilianNpc caller, NpcSquad squad) {
	}

	/**
	 * Callsign = the type's stripped display name; hunted = the squad's target, resolved by uuid; extras: {@code
	 * %faction%}. One instance shared by every squad - nothing here is per-squad state.
	 */
	private final class FactionVoice implements RadioVoice {

		@Override
		public String callsign(AbstractNpc npc) {
			if (npc instanceof CivilianNpc civilian) {
				return ChatColor.stripColor(GanglandChatUtil.color(civilian.getTypeConfig().displayName()));
			}
			return npc.getClass().getSimpleName();
		}

		@Override
		@Nullable
		public LivingEntity hunted(NpcSquad squad) {
			SquadKey key = keyOf.get(squad);
			return key == null ? null : entityById(key.targetId());
		}

		@Override
		public Map<String, String> extras(NpcSquad squad) {
			SquadKey key = keyOf.get(squad);
			return key == null ? Map.of() : Map.of("faction", key.faction());
		}
	}

	// ── Group spawning ────────────────────────────────────────────────────────

	/**
	 * Spawns a complete group from civilians.yml at the given location. All members are registered with the registry
	 * and linked to the group.
	 *
	 * @param location the center spawn location for the group
	 * @param groupId the group key as defined in civilians.yml
	 */
	@Nullable
	public CivilianGroup spawnGroup(Location location, String groupId) {
		CivilianGroupConfig groupConfig = civiliansConfig.groups().get(groupId);
		if (groupConfig == null) {
			log.warn("Unknown civilian group '{}' — skipping spawn.", groupId);
			return null;
		}

		CivilianGroup group = new CivilianGroup(groupId, groupConfig);

		List<CivilianNpc> spawned = new ArrayList<>();

		for (Map.Entry<String, Integer> entry : groupConfig.members().entrySet()) {
			String typeId = entry.getKey();
			int    count  = entry.getValue();

			CivilianTypeConfig typeConfig = civiliansConfig.types().get(typeId);
			if (typeConfig == null) {
				log.warn("Group '{}' references unknown type '{}' — skipping.", groupId, typeId);
				continue;
			}

			for (int i = 0; i < count; i++) {
				CivilianNpc npc = npcFactory.createCivilian(location, typeConfig, groupId, groupConfig);
				if (npc == null) continue;

				npc.setGroup(group);
				group.addMember(npc);
				spawned.add(npc);
			}
		}

		if (!group.isEmpty()) {
			registry.registerGroup(group);
			// Defer registration by 1 tick so Citizens finishes entity initialisation for all group members
			plugin.getServer().getScheduler().runTaskLater(plugin, () -> spawned.forEach(registry::register), 1L);
			log.debug("Spawned group '{}' with {} members at {}.", groupId, group.getMembers().size(), location);
			return group;
		}

		return null;
	}

	// ── Shutdown ──────────────────────────────────────────────────────────────

	/**
	 * Destroys all active civilian NPCs and clears all registries.
	 */
	public void shutdown() {
		for (CivilianNpc npc : registry.getActiveNpcs()) {
			try {
				npc.destroy(markManager::removeMark);
			} catch (Exception e) {
				log.warn("Error destroying civilian NPC during shutdown: {}", e.getMessage());
			}
		}
		registry.clear();
		squads.clear();
		keyOf.clear();           // survives reloads otherwise: nothing prunes an entry whose squad left squads
		pendingRecruits.clear();
	}

	// ── Proximity spawners ────────────────────────────────────────────────────

	/**
	 * Checks all registered spawners each interval. Spawns civilians when a player enters the activation radius and
	 * despawns them when all players leave the despawn radius.
	 */
	private void tickProximitySpawners(CivilianSettings settings) {
		Collection<? extends Player> players = Bukkit.getOnlinePlayers();
		if (players.isEmpty()) {
			spawnManager.getSpawners().forEach(spawner -> despawnFromSpawner(spawner.getId()));
			return;
		}

		double activationRadiusSq = Math.pow(settings.getCivilianSpawnerActivationRadius(), 2);
		double despawnRadiusSq    = Math.pow(settings.getCivilianSpawnerDespawnRadius(), 2);
		double hardLeashSq        = Math.pow(settings.getCivilianSpawnerHardLeashRadius(), 2);
		int    maxNpcs            = settings.getCivilianSpawnerMaxNpcs();
		String defaultTypeId      = settings.getCivilianSpawnerDefaultTypeId();

		for (CivilianSpawner spawner : spawnManager.getSpawners()) {
			Location spawnerLoc = spawner.getLocation();
			if (spawnerLoc.getWorld() == null) continue;

			// Hard leash — mark any NPC that wandered past the leash for removal so its cap slot frees.
			markStrayNpcsForRemoval(spawner.getId(), spawnerLoc, hardLeashSq);

			boolean anyWithinActivation = false;
			boolean anyWithinDespawn    = false;

			for (Player player : players) {
				if (!player.getWorld().equals(spawnerLoc.getWorld())) continue;
				double distSq = player.getLocation().distanceSquared(spawnerLoc);
				if (distSq <= activationRadiusSq) {
					anyWithinActivation = true;
					break;
				}
				if (distSq <= despawnRadiusSq) {
					anyWithinDespawn = true;
				}
			}

			if (anyWithinActivation) {
				if (spawner.getGroupId() != null) {
					// Group spawner — spawn one group if none currently alive from this spawner
					boolean hasActiveGroup = registry.getActiveGroups()
							.stream()
							.anyMatch(g -> Integer.valueOf(spawner.getId()).equals(g.getSpawnerId()) && !g.isEmpty());

					if (!hasActiveGroup) {
						CivilianGroup group = spawnGroup(spawnerLoc, spawner.getGroupId());
						if (group != null) {
							group.setSpawnerId(spawner.getId());
						}
					}
				} else {
					// Individual NPC spawner — fill up to maxNpcs
					long aliveCount = registry.getActiveNpcs()
							.stream()
							.filter(npc -> Integer.valueOf(spawner.getId()).equals(npc.getSpawnerId()))
							.filter(npc -> npc.isValid() && !npc.isMarkedForRemoval())
							.count();

					if (aliveCount < maxNpcs) {
						String typeId = spawner.getTypeId() != null ? spawner.getTypeId() : defaultTypeId;
						if (typeId == null || typeId.isBlank()) continue;

						CivilianNpc npc = spawnManager.spawnCivilian(spawnerLoc, typeId);
						if (npc != null) {
							npc.setSpawnerId(spawner.getId());
						}
					}
				}
			} else if (!anyWithinDespawn) {
				despawnFromSpawner(spawner.getId());
			}
		}
	}

	/**
	 * Marks any civilian whose distance from {@code spawnerLoc} exceeds the hard-leash radius for removal, so the
	 * per-spawner cap frees up and a replacement can spawn.
	 */
	private void markStrayNpcsForRemoval(int spawnerId, Location spawnerLoc, double hardLeashSq) {
		Integer id = spawnerId;

		for (CivilianNpc npc : registry.getActiveNpcs()) {
			if (!id.equals(npc.getSpawnerId())) continue;
			if (npc.isMarkedForRemoval() || !npc.isValid()) continue;

			Location npcLoc = npc.getEntity().getLocation();
			if (!Objects.requireNonNull(npcLoc.getWorld()).equals(spawnerLoc.getWorld())) {
				npc.markForRemoval();
				continue;
			}

			if (npcLoc.distanceSquared(spawnerLoc) > hardLeashSq) {
				npc.markForRemoval();
			}
		}
	}

	/**
	 * Marks all civilians spawned from the given spawner for removal. {@link #tickAll} will destroy them on the next
	 * tick.
	 */
	private void despawnFromSpawner(int spawnerId) {
		// Individual NPCs tracked to this spawner
		registry.getActiveNpcs()
				.stream()
				.filter(npc -> Integer.valueOf(spawnerId).equals(npc.getSpawnerId()))
				.forEach(CivilianNpc::markForRemoval);

		// Group members whose group was spawned from this spawner
		registry.getActiveGroups()
				.stream()
				.filter(g -> Integer.valueOf(spawnerId).equals(g.getSpawnerId()))
				.flatMap(g -> g.getMembers()
						.stream())
				.forEach(CivilianNpc::markForRemoval);
	}

	// ── Internal tick ─────────────────────────────────────────────────────────

	private void tickAll() {
		// Tick NPCs; collect dead ones for removal
		registry.npcMap().entrySet().removeIf(entry -> {
			CivilianNpc npc = entry.getValue();
			if (npc.isMarkedForRemoval() || !npc.isValid()) {
				try {
					npc.destroy(markManager::removeMark);
				} catch (Exception e) {
					log.warn("Error destroying civilian NPC during tick: {}", e.getMessage());
				}
				return true;
			}
			try {
				npc.tick();
			} catch (Exception e) {
				log.warn("Civilian NPC tick threw exception, marking for removal: {}", e.getMessage());
				npc.markForRemoval();
			}
			return false;
		});

		drainPendingRecruits();

		// Clean empty groups
		registry.groupMap().entrySet().removeIf(entry -> {
			CivilianGroup group = entry.getValue();
			group.pruneDeadMembers();
			return group.isEmpty();
		});

		pruneEmptySquads();
	}

	/**
	 * Drops every squad every member has left (gave up, died, despawned), and its {@link #keyOf} entry with it.
	 * Package-private (not {@code private}) so tests can check pruning without the rest of {@link #tickAll}.
	 */
	void pruneEmptySquads() {
		squads.entrySet().removeIf(entry -> {
			boolean empty = entry.getValue().isEmpty();
			if (empty) keyOf.remove(entry.getValue());
			return empty;
		});
	}
}
