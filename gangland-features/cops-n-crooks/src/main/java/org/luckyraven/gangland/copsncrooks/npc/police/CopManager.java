package org.luckyraven.gangland.copsncrooks.npc.police;

import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import lombok.CustomLog;
import lombok.Getter;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.luckyraven.gangland.civilians.npc.CivilianNpcRegistry;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.BackupSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.BreatherSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DispatchSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.HandoffSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.RegroupSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.StuckSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.dispatch.Dispatcher;
import org.luckyraven.gangland.copsncrooks.npc.police.dispatch.PendingUnit;
import org.luckyraven.gangland.copsncrooks.npc.police.dispatch.SpawnBias;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopSightlines;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio.RadioCall;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.npc.police.targeting.TargetingManager;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.gangland.core.downed.DownedPlayerRegistry;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNullElse;
import static org.luckyraven.gangland.copsncrooks.npc.police.perception.CopSightlines.inView;

/**
 * Central manager for all cop NPCs. Handles spawning, AI ticking, and lifecycle management.
 */
@CustomLog
public class CopManager implements BeanLifecycle {

	/** Past twice {@code Recycle_Seconds}, only a view within this many blocks keeps a stranded cop. */
	private static final double OVERDUE_VIEW_BLOCKS = 24.0;

	private final JavaPlugin            plugin;
	@Getter
	private final CopSpawnManager       spawnManager;
	private final TargetingManager      targetingManager;
	private final CopLoader             copLoader;
	private final NpcMarkManager        markManager;
	private final DetainmentService     detainmentService;
	private final Map<UUID, CopGroup>   groups;
	/** Players whose wanted level was RESTOREd and whose first dispatch batch is not logged yet (debug reason). */
	private final java.util.Set<UUID>    restoring = ConcurrentHashMap.newKeySet();
	/** Players whose current wanted clear has already been announced with Stand_Down; reset by the next wanted start. */
	private final java.util.Set<UUID>    stoodDown = new java.util.HashSet<>();
	private final Map<UUID, BukkitTask> aiTasks;
	private final Map<UUID, BukkitTask> spawnTasks;
	private final Set<UUID>             activeCombatAlerts;
	private final Set<UUID>             copAttackers;
	private final CivilianNpcRegistry   civilianNpcRegistry;
	private final CopRadio              copRadio;
	private final CopFieldCare          fieldCare;
	private final Dispatcher            dispatcher;
	/**
	 * Radio calls heard since the last AI tick; answered after the tick's cop loop so a listener never changes a cop
	 * list that is being iterated.
	 */
	private final Deque<RadioCall>      pendingCalls = new ArrayDeque<>();
	private       CopConfigProvider     configProvider;
	/**
	 * Whether a wanted end of this cause keeps the squad on the post-escape search (0.16.1 T-187). The module wires
	 * {@code PostEscapeSearch#isEscape}, which also honours {@code Wanted.Post_Escape.Enable}; the default is the escape cause.
	 */
	private volatile Predicate<WantedCause> escape = cause -> cause == WantedCause.EVASION;
	/** Called on every AI tick of an online player's chase; see {@link #addAiTickHook}. */
	private final List<BiConsumer<Player, @Nullable CopGroup>> aiTickHooks     = new CopyOnWriteArrayList<>();
	private final List<Runnable>                               shutdownHooks  = new CopyOnWriteArrayList<>();
	/** Called first thing when a player hits a cop; see {@link #addCopAttackedHook}. */
	private final List<BiConsumer<CopNpc, Player>>             copAttackedHooks = new CopyOnWriteArrayList<>();
	/** Beat-cop resolvers, asked in order after the group scan; see {@link #addCopResolver}. */
	private final List<Function<Entity, @Nullable CopNpc>>     copResolvers     = new CopyOnWriteArrayList<>();

	public CopManager(JavaPlugin plugin, CopSpawnManager spawnManager, TargetingManager targetingManager,
	                  CopLoader copLoader, NpcMarkManager markManager,
	                  DetainmentService detainmentService, CivilianNpcRegistry civilianNpcRegistry,
	                  CopRadio copRadio, Dispatcher dispatcher) {
		this.plugin            = plugin;
		this.spawnManager      = spawnManager;
		this.targetingManager  = targetingManager;
		this.copLoader         = copLoader;
		this.configProvider    = copLoader.getLoadedProvider();
		this.markManager       = markManager;
		this.detainmentService = detainmentService;
		this.copRadio          = copRadio;
		this.dispatcher        = dispatcher;
		this.fieldCare         = new CopFieldCare(copLoader::getLoadedProvider, copRadio, copRadio::now);

		this.civilianNpcRegistry = civilianNpcRegistry;
		this.groups              = new ConcurrentHashMap<>();
		this.aiTasks             = new ConcurrentHashMap<>();
		this.spawnTasks          = new ConcurrentHashMap<>();
		this.activeCombatAlerts  = ConcurrentHashMap.newKeySet();
		this.copAttackers        = ConcurrentHashMap.newKeySet();
	}

	/**
	 * Called when a player becomes wanted. Starts cop spawning and AI for that player.
	 *
	 * @param player the wanted player
	 * @param wanted the wanted data
	 */
	public void onWantedStart(Player player, Wanted wanted) {
		onWantedStart(player, wanted, WantedCause.UNKNOWN);
	}

	/**
	 * {@link #onWantedStart(Player, Wanted)} knowing why the player became wanted.
	 *
	 * @param cause why the wanted level started
	 */
	public void onWantedStart(Player player, Wanted wanted, WantedCause cause) {
		UUID playerId = player.getUniqueId();
		if (!wanted.isWanted()) return;

		targetingManager.registerWanted(player, wanted);
		stoodDown.remove(playerId);
		// The crime scene is known: the group's squad starts from where the player is now. Dispatch announces a new
		// hunt, or one whose trail went cold, before the squad hears of it.
		CopGroup existing = groups.get(playerId);
		CopGroup group    = groups.computeIfAbsent(playerId, this::newGroup);
		long     now      = copRadio.now();
		// a crime-scene tip counts as fresh as a sighting here: a repeated start right after one stays quiet
		boolean  fresh    = group.getSquad().hasFreshSighting() ||
		                    group.tippedOffWithin(now, NpcSquad.SIGHTING_GRACE_MS);
		if (existing == null || !fresh) {
			copRadio.dispatch(group, player, "Dispatch_Wanted", wanted.getLevel(), tierNameFor(wanted.getLevel()));
		}
		DispatchSettings dispatch = dispatchSettings();
		if (cause == WantedCause.RESTORE && dispatch.enabled()) {
			// A rejoin is no crime scene: nobody saw him come back. His units wait out the rejoin grace (Ruling R21).
			group.setBreatherUntil(Math.max(group.getBreatherUntil(), now + dispatch.rejoinGraceSeconds() * 1000L));
			restoring.add(playerId);
		} else {
			// 0.21.1 (R4): the crime scene is a tip, no cop saw him there - the squad searches the spot instead of
			// chasing his live position; the tip-off stamp holds the evasion clock as the old sighting did
			group.getSquad().reportTip(player.getLocation());
			group.markTipOff(now);
		}

		// A new wanted start is a new episode: pull the group's returning cops back into the hunt instead of letting
		// them walk home for up to Return.Max_Ticks. "Never give up while wanted" outranks the D1 re-engage rule,
		// which only guards against a per-tick bounce inside a single episode. Only up to the new level's target (T-435):
		// the walkers over it keep walking, sent home, and come back first if the group lacks cops later (T-436).
		int target = Math.min(spawnManager.getTargetCopCount(wanted.getLevel()), configProvider.getMaxCopsPerPlayer());
		reengageReturning(group, player, target - engagedCount(group), cop -> true);
		// The new episode fields its own target, trimmed against the cops engaged now, not against the last episode's
		// target: a re-spot after a stepwise escape (Drop_Mode ONE_STAR) lands where an all-stars escape does (T-435)
		group.setPendingRelease(Math.max(0, engagedCount(group) - target));

		startSpawnTask(playerId, wanted);
		startAITask(playerId);
	}

	/**
	 * Called when a player is no longer wanted, and why (0.16.1 T-187). An escape by evasion keeps the squad on the
	 * post-escape search: no Stand_Down, no targeting clear, the AI task runs on. Death and arrest end the hunt outright:
	 * the cops stand down once and the squad is removed. Any other end (admin, sign, bribe, contact, decay, unknown) is the
	 * 0.16.0 stand-down: the cops stop targeting him and walk home.
	 *
	 * @param player the player
	 * @param cause  why the wanted level reached 0
	 */
	public void onWantedEnd(Player player, WantedCause cause) {
		UUID playerId = player.getUniqueId();

		if (escape.test(cause)) {
			keepSquadOnSearch(player);
			return;
		}

		CopGroup group = groups.get(playerId);
		// WantedEndEvent and the level change to 0 both land here for one clear: announce it once per episode.
		if (group != null && !group.isEmpty() && stoodDown.add(playerId)) copRadio.sayFromLeader(group, "Stand_Down");

		if (cause == WantedCause.DEATH || cause == WantedCause.ARREST) {
			endSquad(playerId);
			return;
		}

		targetingManager.unregisterWanted(playerId);
		stopSpawnTask(playerId);
		clearCombatAlert(playerId);
		restoring.remove(playerId);
		if (group != null) {
			group.clearCombatAlert();
			group.clearPending();
			group.setLevel(0);
			pendingCalls.removeIf(call -> call.group() == group);
		}
		if (group == null || group.isEmpty()) {
			stopAITask(playerId);
			groups.remove(playerId);
			return;
		}

		// Clear targeting only for cops still pointing at the now-unwanted player. Do not despawn: they walk home.
		for (CopNpc cop : group.getCops()) {
			if (!playerId.equals(cop.getTargetPlayerId())) continue;

			cop.setTargetPlayerId(null);
			cop.setCombatForced(false);
		}
	}

	/**
	 * The post-escape search ran out on its own (0.16.1 T-187): the cops give up. The cop-attacker lock goes with it, so no
	 * cop keeps hunting someone who hit one. With no target left, the squad walks home on its next AI tick, and the spawn
	 * task stops itself once the player is no longer searched.
	 *
	 * @param playerId the player UUID
	 */
	public void searchGaveUp(UUID playerId) {
		removeCopAttacker(playerId);

		CopGroup group = groups.get(playerId);
		if (group != null && !group.isEmpty() && stoodDown.add(playerId)) copRadio.sayFromLeader(group, "Stand_Down");
	}

	/**
	 * Called when a player is no longer wanted, without a cause: the same as an end by {@link WantedCause#UNKNOWN}.
	 *
	 * @param player the player
	 */
	public void onWantedEnd(Player player) {
		onWantedEnd(player, WantedCause.UNKNOWN);
	}

	/**
	 * Ends the player's squad outright: the targeting, the spawn task, the cop-attacker lock and every cop of the group.
	 * The AI task notices the missing group and stops itself on its next tick. Used when a player dies, is arrested or
	 * quits (0.16.1 T-187).
	 *
	 * @param playerId the player UUID
	 */
	public void endSquad(UUID playerId) {
		targetingManager.unregisterWanted(playerId);
		stopSpawnTask(playerId);
		clearCombatAlert(playerId);
		restoring.remove(playerId);
		removeCopAttacker(playerId);

		CopGroup group = groups.get(playerId);
		if (group != null) {
			group.clearCombatAlert();
			group.clearPending();
			pendingCalls.removeIf(call -> call.group() == group);
		}
		despawnAllForPlayer(playerId);
	}

	/**
	 * Tells the squads which wanted ends are escapes that keep the squad on the search (0.16.1 T-187). Read per end, so it
	 * may be set after construction.
	 */
	public void setEscapePredicate(Predicate<WantedCause> escape) {
		this.escape = escape;
	}

	/** Test seam: whether a cop has been attacked by this player since the last clear. */
	boolean isCopAttacker(UUID playerId) {
		return copAttackers.contains(playerId);
	}

	/**
	 * The escape branch of {@link #onWantedEnd(Player, WantedCause)}: the squad stays on the search and the spawn task runs
	 * on (it keeps a lowest-tier squad, see {@link #spawnTick}). A cop mid-cuff drops the cuff: a searched player is never
	 * cuffed.
	 */
	private void keepSquadOnSearch(Player player) {
		UUID playerId = player.getUniqueId();
		clearCombatAlert(playerId);
		restoring.remove(playerId);

		CopGroup group = groups.get(playerId);
		if (group != null) {
			group.clearCombatAlert();
			group.clearPending();
			pendingCalls.removeIf(call -> call.group() == group);
			for (CopNpc cop : group.getCops()) {
				if (cop.getCurrentState() == CopState.CUFFING) cop.transitionTo(CopState.PURSUING);
			}
			// a drop's release still waiting for free cops (posted ones) never fires inside the search (T-187)
			group.setPendingRelease(0);
			// The owner ruling: the search goes on, so cops already walking home come back to it, every one of them
			// (the search's one-star squad never sends the surplus home: spawnTick)
			reengageReturning(group, player, Integer.MAX_VALUE, cop -> true);
		}
	}

	/**
	 * Pulls up to {@code limit} of the group's returning cops within {@code Pursuit.Max_Distance} of {@code player} that
	 * pass {@code eligible} back into the hunt, nearest first; the eligible ones over the limit are marked sent home
	 * (T-436). One farther out would leash again on its next tick and restart its walk home (T-131, cause 5): it stays
	 * RETURNING. A group with no engaged Commander calls its nearest walking Commander back first, even past the limit,
	 * so the squad's last Commander is never left sent home (T-146); a new episode then trims the others to its target.
	 *
	 * @return the cops called back
	 */
	private List<CopNpc> reengageReturning(CopGroup group, Player player, int limit, Predicate<CopNpc> eligible) {
		double       maxDist = configProvider.getPursuitMaxDistance();
		List<CopNpc> walkers = new ArrayList<>();
		boolean      led     = false;
		for (CopNpc cop : group.getCops()) {
			if (cop.isValid() && cop.getCurrentState() != CopState.RETURNING && isCommander(cop)) led = true;
			if (!cop.isValid() || cop.getCurrentState() != CopState.RETURNING || cop.getEntity() == null) continue;
			if (isStrandedReturning(cop, player, maxDist) || !eligible.test(cop)) continue;
			walkers.add(cop);
		}
		Location at       = player.getLocation();
		boolean  needLead = !led;
		walkers.sort(Comparator.comparing((CopNpc cop) -> !(needLead && isCommander(cop)))
		                       .thenComparingDouble(cop -> cop.getEntity().getLocation().distanceSquared(at)));

		List<CopNpc> back = new ArrayList<>();
		for (CopNpc cop : walkers) {
			boolean leader = needLead && back.isEmpty() && isCommander(cop);
			if (!leader && back.size() >= limit) {
				group.markSentHome(cop);
				continue;
			}
			cop.setTargetPlayerId(player.getUniqueId());
			cop.transitionTo(CopState.PURSUING);
			back.add(cop);
		}
		return back;
	}

	/**
	 * Called when a player's wanted level changes.
	 *
	 * @param player the player
	 * @param wanted the wanted data
	 * @param oldLevel the previous level
	 * @param newLevel the new level
	 */
	public void onWantedLevelChange(Player player, Wanted wanted, int oldLevel, int newLevel) {
		onWantedLevelChange(player, wanted, oldLevel, newLevel, WantedCause.UNKNOWN);
	}

	/**
	 * As {@link #onWantedLevelChange(Player, Wanted, int, int)}, knowing why the level changed: an escape by evasion
	 * keeps the squad on the post-escape search (0.16.1 T-187).
	 *
	 * @param cause why the level changed
	 */
	public void onWantedLevelChange(Player player, Wanted wanted, int oldLevel, int newLevel, WantedCause cause) {
		if (oldLevel == 0 && newLevel > 0) {
			onWantedStart(player, wanted);
		} else if (oldLevel > 0 && newLevel == 0) {
			onWantedEnd(player, cause);
		}
	}

	/**
	 * Called when a cop NPC is attacked by a player. Forces ALL cops assigned to that player into combat mode (alert
	 * system).
	 *
	 * @param copNpc the attacked cop
	 * @param attacker the attacking player
	 */
	public void onCopAttackedAlert(CopNpc copNpc, Player attacker) {
		for (BiConsumer<CopNpc, Player> hook : copAttackedHooks) {
			try {
				hook.accept(copNpc, attacker);
			} catch (RuntimeException exception) {
				log.warn("cop-attacked hook failed: " + exception);
			}
		}
		// Always force the directly attacked cop into combat with the attacker,
		// regardless of whether it currently has a target or belongs to a group.
		onCopAttacked(copNpc, attacker);

		// Alert all other cops in the same group (if any).
		// The cop may not have a targetPlayerId yet (idle/returning), so we cannot
		// rely on groups.get(targetPlayerId). Instead, find the group containing this cop.
		CopGroup group = findGroupContaining(copNpc);
		if (group == null || group.isEmpty()) return;

		// Hitting a cop gives the group's target away: the whole squad learns where he is, and he is resisting. A hit
		// from where the cop cannot see him is a tip of the spot, not a sighting (0.21.1, R4)
		if (attacker.getUniqueId().equals(group.getTargetPlayerId())) {
			if (copNpc.hasLineOfSight(attacker)) group.getSquad().reportSighting(attacker.getLocation());
			else group.getSquad().reportTip(attacker.getLocation());
			group.escalate(attacker.getUniqueId());
		}

		activeCombatAlerts.add(group.getTargetPlayerId());

		for (CopNpc alertedCop : group.getCops()) {
			if (!alertedCop.isValid()) continue;
			if (alertedCop == copNpc) continue;

			onCopAttacked(alertedCop, attacker);
		}
	}

	/**
	 * Called when a cop NPC is attacked by a player. Forces the cop into combat mode.
	 *
	 * @param copNpc the attacked cop
	 * @param attacker the attacking player
	 */
	public void onCopAttacked(CopNpc copNpc, Player attacker) {
		copAttackers.add(attacker.getUniqueId());
		copNpc.setTargetPlayerId(attacker.getUniqueId());
		copNpc.setCombatForced(true);
		copNpc.transitionTo(CopState.COMBAT);
	}

	/**
	 * Removes a player from the cop-attacker registry. Should be called when the player dies or leaves, so that cops no
	 * longer treat them as a combat target after respawn.
	 * <p>
	 * Also eagerly clears this player from any cop's target so cops don't continue navigating to the death location.
	 * The next AI tick's {@code resolveTarget} will transition idle cops to RETURNING if no other target is found.
	 *
	 * @param playerId the player UUID
	 */
	public void removeCopAttacker(UUID playerId) {
		copAttackers.remove(playerId);

		for (CopGroup group : groups.values()) {
			for (CopNpc cop : group.getCops()) {
				if (!playerId.equals(cop.getTargetPlayerId())) continue;

				cop.setTargetPlayerId(null);
				cop.setCombatForced(false);
			}
		}
	}

	/**
	 * Checks if there's an active combat alert for a given player.
	 *
	 * @param playerId the player UUID
	 *
	 * @return true if combat alert is active
	 */
	public boolean hasCombatAlert(UUID playerId) {
		return activeCombatAlerts.contains(playerId);
	}

	/**
	 * Returns all cops assigned to a given player.
	 *
	 * @param playerId the player UUID
	 *
	 * @return list of cop NPCs
	 */
	public List<CopNpc> getCopsForPlayer(UUID playerId) {
		CopGroup group = groups.get(playerId);
		return group != null ? new ArrayList<>(group.getCops()) : Collections.emptyList();
	}

	/**
	 * Checks whether the given entity is a cop NPC managed by this system.
	 *
	 * @param entity the entity to check
	 *
	 * @return true if it's a cop
	 */
	public boolean isCopNpc(org.bukkit.entity.Entity entity) {
		for (CopGroup group : groups.values()) {
			for (CopNpc cop : group.getCops()) {
				if (!validateEntityCop(entity, cop)) continue;

				return true;
			}
		}
		return resolveCop(entity) != null;
	}

	/**
	 * Finds the CopNpc associated with the given entity.
	 *
	 * @param entity the entity
	 *
	 * @return the cop npc, or null
	 */
	public CopNpc findCopByEntity(org.bukkit.entity.Entity entity) {
		for (CopGroup group : groups.values()) {
			for (CopNpc cop : group.getCops()) {
				if (!validateEntityCop(entity, cop)) continue;

				return cop;
			}
		}
		return resolveCop(entity);
	}

	/**
	 * The cop whose entity is {@code entity}, even while that entity is dying: {@link #findCopByEntity} requires a
	 * valid entity, and an entity is no longer valid during its {@code EntityDeathEvent}. Once Citizens has despawned
	 * the NPC (its LOW death handler) {@code getNpc().getEntity()} is null, so the body is also resolved through the
	 * Citizens registry, which reads the NPC straight off the body.
	 */
	public @Nullable CopNpc findDyingCop(Entity entity) {
		// no lambda: its synthetic method would put a Citizens type in this class's signatures (CitizensBlindScan)
		var registry = NpcSupport.registry();
		NPC bodyNpc  = registry.isPresent() ? registry.get().getNPC(entity) : null;
		for (CopGroup group : groups.values()) {
			synchronized (group.getCops()) {
				for (CopNpc cop : group.getCops()) {
					if (bodyNpc != null && cop.getNpc() == bodyNpc) return cop;
					Entity copEntity = cop.getNpc().getEntity();
					if (copEntity != null && copEntity.getUniqueId().equals(entity.getUniqueId())) return cop;
				}
			}
		}
		return resolveCop(entity);
	}

	/**
	 * Shuts down all cops and cancels all tasks. Called on plugin disable.
	 */
	public void shutdown() {
		for (UUID playerId : new HashSet<>(spawnTasks.keySet())) {
			stopSpawnTask(playerId);
		}
		for (UUID playerId : new HashSet<>(aiTasks.keySet())) {
			stopAITask(playerId);
		}
		for (UUID playerId : new HashSet<>(groups.keySet())) {
			despawnAllForPlayer(playerId);

			// the stopped AI task can no longer tell the hooks the group is gone (the evasion clock turns OFF there)
			Player player = Bukkit.getPlayer(playerId);
			if (player != null) runAiTickHooks(player, null);
		}
		copAttackers.clear();
		for (Runnable hook : shutdownHooks) hook.run();
	}

	/**
	 * Registers a hook run at the end of {@link #shutdown()}, after the cops are gone: a reload or disable ends the
	 * post-escape search through it (0.16.1 wanted-6).
	 */
	public void addShutdownHook(Runnable hook) {
		shutdownHooks.add(hook);
	}

	@Override
	public void onPreClear() {
		shutdown();
	}

	@Override
	public void onClear() {
		groups.clear();
		aiTasks.clear();
		spawnTasks.clear();
		activeCombatAlerts.clear();
		copAttackers.clear();
		pendingCalls.clear();
		configProvider = null;
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (firstLoad) return;
		this.configProvider = copLoader.getLoadedProvider();
	}

	@Override
	public void onShutdown() {
		shutdown();
	}

	/**
	 * Finds the {@link CopGroup} that contains the given cop, or {@code null} if the cop is not in any group.
	 */
	private CopGroup findGroupContaining(CopNpc copNpc) {
		for (CopGroup group : groups.values()) {
			if (group.getCops().contains(copNpc)) return group;
		}
		return null;
	}

	/**
	 * The group hunting {@code playerId}, or {@code null}. Package-private test seam.
	 */
	CopGroup groupFor(UUID playerId) {
		return groupOf(playerId);
	}

	/** The group hunting {@code playerId}, or {@code null}. */
	public @Nullable CopGroup groupOf(UUID playerId) {
		return groups.get(playerId);
	}

	/**
	 * Registers a hook run on every AI tick of an online player's chase: at the end of a normal tick, and before the
	 * early return when his group is missing or empty (the group is passed as found, possibly {@code null}). A hook that
	 * throws is logged and skipped.
	 */
	public void addAiTickHook(BiConsumer<Player, @Nullable CopGroup> hook) {
		aiTickHooks.add(hook);
	}

	/**
	 * Registers a resolver for cops that belong to no {@link CopGroup} (beat cops): {@link #isCopNpc},
	 * {@link #findCopByEntity} and {@link #findDyingCop} consult the group scan first, then the resolvers in order; the
	 * first non-null wins and a throwing resolver is logged and skipped.
	 */
	public void addCopResolver(Function<Entity, @Nullable CopNpc> resolver) {
		copResolvers.add(Objects.requireNonNull(resolver, "resolver"));
	}

	private @Nullable CopNpc resolveCop(Entity entity) {
		for (Function<Entity, @Nullable CopNpc> resolver : copResolvers) {
			try {
				CopNpc cop = resolver.apply(entity);
				if (cop != null) return cop;
			} catch (RuntimeException exception) {
				log.warn("cop resolver failed: " + exception);
			}
		}
		return null;
	}

	/** Registers a hook run first thing when a player hits a cop; a hook that throws is logged and skipped. */
	public void addCopAttackedHook(BiConsumer<CopNpc, Player> hook) {
		copAttackedHooks.add(hook);
	}

	private void runAiTickHooks(Player player, @Nullable CopGroup group) {
		for (BiConsumer<Player, @Nullable CopGroup> hook : aiTickHooks) {
			try {
				hook.accept(player, group);
			} catch (RuntimeException exception) {
				log.warn("AI-tick hook failed: " + exception);
			}
		}
	}

	/** A new group whose squads speak on the police radio and queue its calls here. */
	private CopGroup newGroup(UUID playerId) {
		CopGroup group = new CopGroup(playerId);
		group.setListener(copRadio.listenerFor(group, pendingCalls::add));
		return group;
	}

	private String tierNameFor(int wantedLevel) {
		return CopRadio.tierName(configProvider.getTierConfig(spawnManager.getTierForWantedLevel(wantedLevel)));
	}

	private BackupSettings backupSettings() {
		BackupSettings backup = configProvider.getBackupSettings();
		return backup != null ? backup : BackupSettings.DEFAULT;
	}

	/**
	 * Clears the combat alert for a player when wanted status ends.
	 *
	 * @param playerId the player UUID
	 */
	private void clearCombatAlert(UUID playerId) {
		activeCombatAlerts.remove(playerId);
	}

	private boolean validateEntityCop(Entity entity, CopNpc cop) {
		return cop.isValid() && cop.getNpc().getEntity().getUniqueId().equals(entity.getUniqueId());
	}

	/**
	 * Starts the periodic spawn task for a player.
	 *
	 * @param playerId the player UUID
	 * @param wanted the wanted data
	 */
	private void startSpawnTask(UUID playerId, Wanted wanted) {
		if (spawnTasks.containsKey(playerId)) return;

		BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> spawnTick(playerId, wanted), 20L,
		                                                     configProvider.getSpawnCheckRate());

		spawnTasks.put(playerId, task);
	}

	/**
	 * Stops the spawn task for a player.
	 *
	 * @param playerId the player UUID
	 */
	private void stopSpawnTask(UUID playerId) {
		BukkitTask task = spawnTasks.remove(playerId);
		if (task != null) task.cancel();
	}

	/**
	 * Starts the AI tick task for a player's cops.
	 *
	 * @param playerId the player UUID
	 */
	private void startAITask(UUID playerId) {
		if (aiTasks.containsKey(playerId)) return;

		BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> aiTick(playerId), 0L,
		                                                     configProvider.getAiTickRate());

		aiTasks.put(playerId, task);
	}

	/** One spawn-task run for {@code playerId}'s group. Package-private test seam. */
	void spawnTick(UUID playerId, Wanted wanted) {
		Player player = Bukkit.getPlayer(playerId);
		if (player == null || !player.isOnline()) {
			stopSpawnTask(playerId);
			despawnAllForPlayer(playerId);
			return;
		}

		int wantedLevel = wanted.getLevel();
		if (wantedLevel <= 0) {
			if (!targetingManager.isSearching(playerId)) {
				stopSpawnTask(playerId);
				CopGroup decayed = groups.get(playerId);
				if (decayed != null) decayed.setLevel(0);
				return;
			}
			// A post-escape search keeps a squad at the lowest tier until it runs out (0.16.1 T-187)
			wantedLevel = 1;
		}

		// check if the player was detained before spawning a new cop
		if (detainmentService.isRestrained(player)) {
			return;
		}

		CopGroup group = groups.get(playerId);
		if (group == null) return;

		List<CopNpc> cops    = group.getCops();
		long         now     = copRadio.now();
		double       maxDist = configProvider.getPursuitMaxDistance();

		cops.removeIf(cop -> {
			if (cop.isMarkedForRemoval() || recycles(cop, group, player, now)) {
				group.release(cop, markManager);
				return true;
			}
			// R3 (T-131/T-149): a leashed walker nobody is looking at leaves the map instead of walking home. No tip (R4):
			// nobody saw him, so the refill inherits the squad's own lead and its widening search circle
			if (isStrandedReturning(cop, player, maxDist) && !isWatched(cop)) {
				group.release(cop, markManager);
				return true;
			}
			if (!cop.isValid()) {
				// PLAYER-type Citizens NPCs may have a null entity for a tick or two while initializing —
				// keep in list so the count is not artificially low, causing a spawn loop.
				NPC npc = cop.getNpc();
				if (npc.isSpawned() && npc.getEntity() == null) {
					return false;
				}
				group.release(cop, markManager);
				return true;
			}
			return false;
		});

		BackupSettings backup     = backupSettings();
		int            tier       = spawnManager.getTierForWantedLevel(wantedLevel);
		CopTierConfig  tierConfig = configProvider.getTierConfig(tier);

		if (tierConfig != null) group.getSquad().setFormationArc(tierConfig.tactics().formationArc());
		group.setLevel(wantedLevel);
		group.setTierName(CopRadio.tierName(tierConfig));
		if (group.getLastTier() > 0 && tier > group.getLastTier()) {
			copRadio.dispatch(group, player, "Escalate", wantedLevel, group.getTierName());
		}
		group.setLastTier(tier);

		int baseTarget   = Math.min(spawnManager.getTargetCopCount(wantedLevel), configProvider.getMaxCopsPerPlayer());
		int targetCount  = Math.min(baseTarget + group.backupExtra(now, backup), configProvider.getMaxCopsPerPlayer());
		// A RETURNING cop beyond the pursuit range cannot engage: it is walking home, not part of this hunt's count. Nor
		// is one the group sent home (T-436), but while the group lacks cops the nearest in range comes back first.
		List<CopNpc> counted = new ArrayList<>(cops.stream().filter(
				c -> !isStrandedReturning(c, player, maxDist) && !group.isSentHome(c)).toList());
		counted.addAll(reengageReturning(group, player, targetCount - counted.size(), group::isSentHome));
		int currentCount = counted.size();
		// Each new cop takes the composition's first role no counted cop holds, so a stranded one's role is refilled.
		List<CopRole> composition = configProvider.getSquadComposition(wantedLevel);
		List<CopRole> liveRoles   = new ArrayList<>();
		for (CopNpc cop : counted) liveRoles.add(cop.getRole());

		if (!dispatchSettings().enabled() && !breatherSettings().enabled()) {
			// Spawn all missing cops in one pass so a full wipe is recovered in a single interval
			while (currentCount < targetCount) {
				int     slot   = CopRole.nextSlot(composition, liveRoles);
				CopRole role   = slot < 0 ? null : composition.get(slot);
				CopNpc  newCop = spawnManager.spawnNearPlayer(player, slotTier(wantedLevel, slot, tier),
				                                              loc -> !group.isAvoided(loc, now), role);
				if (newCop == null) break; // no valid location found - stop trying this interval

				newCop.setTargetPlayerId(playerId);

				// New spawns pursue immediately; combatForced flag causes them to enter COMBAT once in range
				newCop.setCombatForced(hasCombatAlert(playerId) || group.isCombatAlert());
				newCop.transitionTo(CopState.PURSUING);

				group.add(newCop);
				liveRoles.add(role);
				currentCount++;
			}
		} else {
			int lacking = targetCount - currentCount;
			// cause 4: units no longer needed (the level dropped, a backup ran out) are called off before they arrive,
			// newest first, so the queue never holds more than the group lacks
			group.trimPending(Math.max(0, lacking));
			dispatchMissing(player, group, wantedLevel, tier, lacking, composition, liveRoles, now);
			spawnDueUnits(player, group, now);
		}

		if (group.isRegrouping()) {
			RegroupSettings r = configProvider.getRegroupSettings() != null ? configProvider.getRegroupSettings()
			                                                                 : RegroupSettings.DEFAULT;
			long arrived = cops.stream().filter(c -> isArrived(c, player, r.arrivalRadius())).count();
			if (arrived >= targetCount || !group.isFallingBack(now)) {
				group.endRegroup();
				// Backup off: the fall-back was silent (no Regroup line), so no "backup's here" push either
				if (backup.enabled() && backup.extraCops() > 0)
					copRadio.sayFromLeaderLater(group, "Regroup_Push", 1, () -> true);
			}
		}

		group.consumeBackupExpiry(now, backup);
		// T-435: a lowered target (a level drop, a new episode at a lower level) leaves live cops over it, and trimPending
		// only calls off queued units: the surplus goes home like an expired backup's extras. Only on a drop: radio
		// responders joining at the same target stay, and the post-escape search (level 0, run at one star) keeps every
		// cop it called back (T-187)
		if (wanted.getLevel() > 0) {
			if (baseTarget < group.getLastTarget() && currentCount > targetCount)
				group.setPendingRelease(Math.max(group.getPendingRelease(), currentCount - targetCount));
			group.setLastTarget(baseTarget);
		}
		if (group.getPendingRelease() > 0) releaseSurplus(group, targetCount, composition);
		group.pruneAttackerSquads();
	}

	/**
	 * Queues a unit for every cop the group lacks beyond those already en route (CONTRACTS C8): a squad wiped inside
	 * {@code Wipe_Window_Seconds} first waits out the breather; each unit arrives after the hold plus its station's ETA,
	 * carrying the hand-off bias active now. One radio line per batch: {@code Wipe_Refill} after a wipe, else
	 * {@code Dispatch_En_Route} when a station sends it.
	 *
	 * @param lacking cops the group lacks in the world ({@code targetCount - counted})
	 */
	private void dispatchMissing(Player player, CopGroup group, int wantedLevel, int starTier, int lacking,
	                             @Nullable List<CopRole> composition, List<CopRole> liveRoles, long now) {
		BreatherSettings breather = breatherSettings();
		// only a casualty after the current breather is a new wipe: a breather shorter than the window ends while the
		// refill is still queued and the old casualty is still inside it
		boolean wipe = breather.enabled() && liveRoles.isEmpty() &&
		               group.casualtyWithin(now, breather.wipeWindowSeconds() * 1000L) &&
		               group.getLastCasualtyAt() > group.getBreatherUntil();
		if (wipe) group.setBreatherUntil(now + breather.breatherMs(wantedLevel));

		int missing = lacking - group.pendingCount();
		// a wipe is radioed and holds the queue even when every missing slot was already queued before it
		if (missing <= 0 && !wipe) return;

		SpawnBias       bias     = group.biasAt(now);
		Dispatcher.Plan plan     = dispatcher.plan(player, now, bias);
		long            hold     = Math.max(0L, group.getBreatherUntil() - now);
		long            arriveAt = now + hold + plan.etaMs();
		// R19: the whole refill arrives after the breather, units queued before the wipe included
		if (wipe) group.holdPendingUntil(arriveAt);
		List<CopRole>   held     = new ArrayList<>(liveRoles);
		for (PendingUnit unit : group.getPending()) held.add(unit.role());
		for (int i = 0; i < missing; i++) {
			int     slot = CopRole.nextSlot(composition, held);
			CopRole role = slot < 0 ? null : composition.get(slot);
			group.enqueue(new PendingUnit(role, slotTier(wantedLevel, slot, starTier), arriveAt, plan.station(), bias));
			held.add(role);
		}

		String eta = String.valueOf((arriveAt - now + 999) / 1000);
		if (wipe) {
			copRadio.dispatch(group, player, "Wipe_Refill", wantedLevel, group.getTierName(), Map.of("eta", eta));
		} else if (plan.station() != null) {
			copRadio.dispatch(group, player, "Dispatch_En_Route", wantedLevel, group.getTierName(),
			                  Map.of("count", String.valueOf(missing), "station", plan.station().getName(), "eta", eta));
		}

		String reason = wipe ? "wipe" : restoring.remove(player.getUniqueId()) ? "restore" : "none";
		log.debug("DISPATCH {} count={} station={} eta={}s hold={}s reason={} bias={}", player.getName(),
		          Math.max(0, missing),
		          plan.station() == null ? "ring" : plan.station().getName(), plan.etaMs() / 1000, (hold + 999) / 1000,
		          reason, bias != null);
	}

	/**
	 * Spawns the queued units that are due. The first one that finds no spot stops the run (the 0.15 "stop trying this
	 * interval" rule): the rest of the due units are requeued untried, ahead of it, so a suspect with no valid spot around him
	 * costs one failed search per run, not one per unit; a requeued unit drops a bias that ran out (T-434). The first
	 * unit spawning under a hand-off bias seeds the squad with the bias's last sighting as a tip plus a tip-off
	 * (CONTRACTS C11), unless a sighting or tip since the hand-off is fresher ({@link CopGroup#takeBiasSeed}).
	 */
	private void spawnDueUnits(Player player, CopGroup group, long now) {
		UUID      playerId = player.getUniqueId();
		SpawnBias active   = group.biasAt(now);
		List<PendingUnit> due = group.takeDue(now);
		for (int i = 0; i < due.size(); i++) {
			PendingUnit queued = due.get(i);
			PendingUnit unit   = queued;
			// a unit queued before the hand-off still spawns ahead of him and seeded while its bias is active
			if (unit.bias() == null && active != null) unit = withBias(unit, active);
			CopNpc newCop = spawnManager.spawnUnit(player, unit, loc -> !group.isAvoided(loc, now));
			if (newCop == null) {
				// the untried units go first next run: a unit refused at its one spot must not block those behind it.
				// Requeued as queued (a borrowed bias is borrowed again only while active), less a bias that ran out
				due.subList(i + 1, due.size()).forEach(next -> group.requeue(dropExpiredBias(next, now)));
				group.requeue(dropExpiredBias(queued, now));
				return;
			}

			newCop.setTargetPlayerId(playerId);
			newCop.setCombatForced(hasCombatAlert(playerId) || group.isCombatAlert());
			newCop.transitionTo(CopState.PURSUING);
			group.add(newCop);

			if (group.takeBiasSeed(unit.bias())) {
				// a tip, not a sighting (R4): no member saw him at the hand-off spot. The units head on along the heading
				// the squad saw him take, Lead_Blocks past it, and search there (R5)
				group.getSquad().reportTip(unit.bias().lead(handoffLead()));
				group.markTipOff(now);
			}
		}
	}

	private static PendingUnit withBias(PendingUnit unit, @Nullable SpawnBias bias) {
		return new PendingUnit(unit.role(), unit.tier(), unit.arriveAt(), unit.station(), bias);
	}

	/** {@code unit} without its bias once that ran out at {@code now}; as is otherwise. */
	private static PendingUnit dropExpiredBias(PendingUnit unit, long now) {
		return unit.bias() == null || unit.bias().activeAt(now) ? unit : withBias(unit, null);
	}

	/**
	 * The tier composition slot {@code slot} spawns at: its {@code "Role@tier"} tier (clamped to the top tier) when the
	 * slot has one, else {@code starTier}, the wanted level's.
	 */
	private int slotTier(int wantedLevel, int slot, int starTier) {
		List<Integer> tiers = requireNonNullElse(configProvider.getSquadTiers(wantedLevel), List.of());
		if (slot < 0 || slot >= tiers.size() || tiers.get(slot) == null || tiers.get(slot) <= 0) return starTier;
		return Math.min(tiers.get(slot), configProvider.getMaxTier());
	}

	private DispatchSettings dispatchSettings() {
		return requireNonNullElse(configProvider.getDispatchSettings(), DispatchSettings.DEFAULT);
	}

	/**
	 * {@code Cops.Handoff.Lead_Blocks}, clamped inside the spawn leash margin ({@link CopSpawnManager#LEASH_MARGIN} x
	 * {@code Pursuit.Max_Distance}; no leash configured keeps it as set).
	 */
	private double handoffLead() {
		double lead  = requireNonNullElse(configProvider.getHandoffSettings(), HandoffSettings.DEFAULT).leadBlocks();
		double leash = configProvider.getPursuitMaxDistance() * CopSpawnManager.LEASH_MARGIN;
		return leash > 0 ? Math.min(lead, leash) : lead;
	}

	private BreatherSettings breatherSettings() {
		return requireNonNullElse(configProvider.getBreatherSettings(), BreatherSettings.DEFAULT);
	}

	/** A valid, non-returning cop in the player's world within {@code radius} of him. */
	private static boolean isArrived(CopNpc cop, Player player, double radius) {
		LivingEntity body = cop.getEntity();
		return cop.isValid() && cop.getCurrentState() != CopState.RETURNING && body != null &&
		       body.getWorld() == player.getWorld() && body.getLocation().distance(player.getLocation()) <= radius;
	}

	/**
	 * A cop hunting the group's suspect that has found no way to him for {@code Cops.Stuck.Recycle_Seconds}
	 * ({@link CopNpc#millisUnreachable()}) and that no one is looking at is taken off the map; its spawner is skipped
	 * for {@code Avoid_Spawner_Seconds} so the replacement spawned in the same run comes from elsewhere. A recycle is
	 * no news of him (0.21.1, R4): the replacement inherits the squad's last-known spot and its widening search
	 * circle, never his live position, and the evasion clock keeps running, so a lead that stays dead ends in his
	 * escape rather than a recycle loop (T-147). Past twice {@code Recycle_Seconds} a view only protects a
	 * cop within {@link #OVERDUE_VIEW_BLOCKS} of the eyes it is in, the suspect's and every other player's alike.
	 */
	private boolean recycles(CopNpc cop, CopGroup group, Player player, long now) {
		if (group.isStandoff(now)) return false;
		StuckSettings stuck = configProvider.getStuckSettings();
		CopState      state = cop.getCurrentState();
		LivingEntity  body  = cop.getEntity();
		if (!stuck.enabled() || body == null || (state != CopState.PURSUING && state != CopState.COMBAT)) return false;
		if (!player.getUniqueId().equals(cop.getTargetPlayerId())) return false;
		if (cop.millisUnreachable() < stuck.recycleSeconds() * 1000L) return false;

		Location at      = body.getLocation();
		boolean  overdue = cop.millisUnreachable() >= 2L * stuck.recycleSeconds() * 1000L;
		if (at.getWorld() == player.getWorld()) {
			// a melee cop knocked off its surround slot keeps its clock running though it can hit him - not through
			// a ceiling or a wall: one a floor up or in the next room is within the distance but cannot reach him
			Location feet = player.getLocation();
			if (at.distance(feet) <= configProvider.getMeleeProfile().reach() &&
			    Math.abs(at.getY() - feet.getY()) <= 1.5 && player.hasLineOfSight(body)) return false;
			// as far out as bystanders are protected (isVisibleToOtherPlayers) and never under 24 blocks; past twice
			// the threshold only the 24 blocks hold, so a cop at a far window he keeps watching is not kept forever
			double protect = overdue ? OVERDUE_VIEW_BLOCKS
			                         : Math.max(OVERDUE_VIEW_BLOCKS, configProvider.getVisibilityCheckDistance());
			if (inView(player, body, protect)) return false;
		}
		// the same bound for bystanders: the spawner's yaw-only check sees through walls to Visibility_Check_Distance
		if (overdue ? inViewOfBystander(at, body, player, OVERDUE_VIEW_BLOCKS)
		            : spawnManager.isVisibleToOtherPlayers(at, player))
			return false;

		Location origin = cop.getSpawnLocation();
		if (origin != null && stuck.avoidSpawnerSeconds() > 0)
			group.avoid(origin, now + stuck.avoidSpawnerSeconds() * 1000L);
		return true;
	}

	/**
	 * A real player other than {@code suspect} has {@code body} {@link CopSightlines#inView} within {@code maxDistance}.
	 * Citizens NPCs (cops are PLAYER-type, so the walker itself) are in {@code world.getPlayers()} and are skipped.
	 */
	private static boolean inViewOfBystander(Location at, LivingEntity body, @Nullable Player suspect,
	                                         double maxDistance) {
		if (at.getWorld() == null) return false;
		for (Player other : at.getWorld().getPlayers())
			if (!other.equals(suspect) && !other.hasMetadata("NPC") && inView(other, body, maxDistance)) return true;
		return false;
	}

	/**
	 * A real player, the suspect or a bystander, has the cop {@link CopSightlines#inView} (line of sight) within
	 * {@code max(24, Visibility_Check_Distance)}. Not {@link CopSpawnManager#isVisibleToOtherPlayers}: it counts NPCs.
	 */
	private boolean isWatched(CopNpc cop) {
		LivingEntity body = cop.getEntity();
		if (body == null) return false;
		double protect = Math.max(OVERDUE_VIEW_BLOCKS, configProvider.getVisibilityCheckDistance());
		return inViewOfBystander(body.getLocation(), body, null, protect);
	}

	/** A RETURNING cop past {@code Pursuit.Max_Distance} of {@code player}: out of this hunt, the spawn task refills it. */
	public boolean isStranded(CopNpc cop, Player player) {
		return isStrandedReturning(cop, player, configProvider.getPursuitMaxDistance());
	}

	private static boolean isStrandedReturning(CopNpc cop, Player player, double maxDist) {
		if (cop.getCurrentState() != CopState.RETURNING || cop.getEntity() == null) return false;
		Location at = cop.getEntity().getLocation();
		return at.getWorld() != player.getWorld() || at.distanceSquared(player.getLocation()) > maxDist * maxDist;
	}

	/**
	 * Sends the backup's extras home once it ran out, and the cops over a lowered target (a new episode at a lower
	 * level, a level drop: T-435), until the group is back to {@code targetCount}: the newest free
	 * cops of a role the group holds more of than its base squad of {@code targetCount} ({@link CopRole#nextRole}), so a
	 * replacement spawned mid-backup for a fallen Commander or Pointman stays (T-146). The group's last Commander is
	 * never sent, even when the wanted level fell to a composition without one. A cop in a fight, cuffing or guarding
	 * is never sent; what cannot go this run is retried on the next. Queued units count toward the group and a surplus
	 * one still en route is called off before any live cop is sent.
	 */
	private void releaseSurplus(CopGroup group, int targetCount, @Nullable List<CopRole> composition) {
		List<CopNpc> live = new ArrayList<>();
		synchronized (group.getCops()) {
			for (CopNpc cop : group.getCops())
				if (cop.isValid() && cop.getCurrentState() != CopState.RETURNING) live.add(cop);
		}
		List<PendingUnit> queued = group.getPending();

		int surplus = live.size() + queued.size() - targetCount;
		if (surplus <= 0) {
			group.setPendingRelease(0);
			return;
		}

		// live and queued cops per role less the base squad's: what is left over came with the backup (roles off: every cop)
		Map<String, Integer> excess = new HashMap<>();
		for (CopNpc cop : live) excess.merge(roleName(cop.getRole()), 1, Integer::sum);
		for (PendingUnit unit : queued) excess.merge(roleName(unit.role()), 1, Integer::sum);
		List<CopRole> base = new ArrayList<>();
		for (int i = 0; i < targetCount; i++) base.add(CopRole.nextRole(composition, base));
		for (CopRole role : base) excess.merge(roleName(role), -1, Integer::sum);
		long commanders = live.stream().filter(CopManager::isCommander).count() +
		                  queued.stream().filter(unit -> unit.role() != null && unit.role().commander()).count();

		// a backup unit still en route is called off first, newest first: it outlived the backup on a long ETA
		for (int i = queued.size() - 1; i >= 0 && surplus > 0 && group.getPendingRelease() > 0; i--) {
			CopRole role = queued.get(i).role();
			if (excess.get(roleName(role)) <= 0) continue;
			if (role != null && role.commander()) {
				if (commanders <= 1) continue; // the squad's leader stays (T-146)
				commanders--;
			}

			excess.merge(roleName(role), -1, Integer::sum);
			queued.remove(i);
			group.setPendingRelease(group.getPendingRelease() - 1);
			surplus--;
		}

		for (int i = live.size() - 1; i >= 0 && surplus > 0 && group.getPendingRelease() > 0; i--) {
			CopNpc   cop   = live.get(i);
			CopState state = cop.getCurrentState();
			String   role  = roleName(cop.getRole());
			if (state != CopState.PURSUING && state != CopState.IDLE || excess.get(role) <= 0) continue;
			if (isCommander(cop)) {
				if (commanders <= 1) continue; // the squad's leader stays (T-146)
				commanders--;
			}

			excess.merge(role, -1, Integer::sum);
			cop.transitionTo(CopState.RETURNING);
			group.markSentHome(cop);
			group.setPendingRelease(group.getPendingRelease() - 1);
			surplus--;
		}
	}

	private static int engagedCount(CopGroup group) {
		return (int) group.getCops().stream()
		                  .filter(cop -> cop.isValid() && cop.getCurrentState() != CopState.RETURNING).count();
	}

	private static boolean isCommander(CopNpc cop) {
		return cop.getRole() != null && cop.getRole().commander();
	}

	private static @Nullable String roleName(@Nullable CopRole role) {
		return role == null ? null : role.name();
	}

	/** One AI-task run for {@code playerId}'s group. Package-private test seam. */
	void aiTick(UUID playerId) {
		Player player = Bukkit.getPlayer(playerId);
		if (player == null || !player.isOnline()) {
			stopAITask(playerId);
			return;
		}

		CopGroup group = groups.get(playerId);
		if (group == null || group.isEmpty()) {
			runAiTickHooks(player, group);
			// Self-cleanup: player is no longer wanted and all cops are gone
			if (!targetingManager.isWanted(playerId)) {
				stopAITask(playerId);
				groups.remove(playerId);
				if (group != null) fieldCare.clear(group);
			}
			drainRadioCalls();
			return;
		}

		List<CopNpc> cops = group.getCops();

		Iterator<CopNpc> iterator = cops.iterator();
		while (iterator.hasNext()) {
			CopNpc cop = iterator.next();

			if (cop.isMarkedForRemoval()) {
				group.release(cop, markManager);
				iterator.remove();
				continue;
			}

			if (!cop.isValid()) {
				// PLAYER-type Citizens NPCs may have a null entity for a tick or two while initializing —
				// skip this tick instead of destroying so the NPC gets a chance to fully spawn.
				NPC npc = cop.getNpc();
				if (npc.isSpawned() && npc.getEntity() == null) {
					continue;
				}
				group.release(cop, markManager);
				iterator.remove();
				continue;
			}

			LivingEntity target = resolveTarget(cop, player);
			cop.tick(target);
			// The cop's hit may have ended the squad (a kill or a down ends the hunt): its list is cleared by now, so the
			// iterator must not be advanced again (0.16.1 T-187)
			if (groups.get(playerId) != group) break;
		}
		// The squad was ended mid-tick: the group is gone, so the hooks see no group and nothing else runs on it
		if (groups.get(playerId) != group) {
			runAiTickHooks(player, null);
			drainRadioCalls();
			return;
		}
		fieldCare.tick(group);

		if (group.pollResisting()) {
			copRadio.sayFromLeader(group, "Resisting");
			fightResisting(group);
		}
		runAiTickHooks(player, group);
		drainRadioCalls();
	}

	/** The suspect resisted: every cop still after him turns from cuffing to fighting. */
	private void fightResisting(CopGroup group) {
		for (CopNpc cop : new ArrayList<>(group.getCops())) {
			if (!cop.isValid() || !group.getTargetPlayerId().equals(cop.getTargetPlayerId())) continue;
			CopState state = cop.getCurrentState();
			if (state == CopState.GUARDING || state == CopState.RETURNING) continue;

			cop.setCombatForced(true);
			cop.transitionTo(CopState.COMBAT);
		}
	}

	/**
	 * Answers the radio calls heard this tick: up to {@code Responder_Max} cops of other groups within {@code Range} of
	 * a call, that are walking home, idle or chasing a civilian, join the calling squad. A cop cuffing, guarding or hunting a player is
	 * never pulled; nor is anyone sent after a suspect already restrained.
	 */
	void drainRadioCalls() {
		RadioCall call;
		while ((call = pendingCalls.poll()) != null) {
			CopGroup group = call.group();
			if (groups.get(group.getTargetPlayerId()) != group) continue;

			LivingEntity hunted = copRadio.huntedOf(group, call.squad());
			if (hunted == null || !hunted.isValid() || hunted.isDead()) continue;
			if (hunted instanceof Player huntedPlayer && detainmentService.isRestrained(huntedPlayer)) continue;

			var settings = configProvider.getRadioSettings();
			int max      = settings != null ? settings.responderMax() : 0;
			if (max <= 0) continue;
			double range = settings.range();

			int live = 0;
			synchronized (group.getCops()) {
				for (CopNpc cop : group.getCops())
					if (cop.isValid() && cop.getCurrentState() != CopState.RETURNING) live++;
			}
			int room = configProvider.getMaxCopsPerPlayer() - live;
			if (room <= 0) continue;

			for (CopNpc responder : responders(call, range, Math.min(max, room))) {
				CopGroup from = responder.getGroup();
				if (from != group) {
					if (from != null) from.detach(responder);
					group.add(responder);
				}

				// An entity target outranks everything in resolveTarget: drop the civilian first
				responder.setTargetEntity(null);
				UUID groupTarget = group.getTargetPlayerId();
				if (groupTarget.equals(hunted.getUniqueId())) {
					responder.setTargetPlayerId(groupTarget);
					responder.setCombatForced(hasCombatAlert(groupTarget) || group.isCombatAlert());
					responder.transitionTo(CopState.PURSUING);
				} else {
					if (hunted instanceof Player attacker && !NpcSupport.isNpc(attacker)) {
						responder.setTargetPlayerId(attacker.getUniqueId());
					} else {
						responder.setTargetEntity(hunted);
					}
					responder.setCombatForced(true);
					responder.transitionTo(CopState.COMBAT);
				}
				copRadio.respond(group, call.squad(), responder);
			}
		}
	}

	/**
	 * The nearest cops of other groups free to answer {@code call}, at most {@code limit}. The calling group's own cops
	 * are never pulled: one walking home was released after a backup or rotated out, and pulling it back would undo
	 * that (a released backup never leaving, or the D1 PURSUING/RETURNING bounce).
	 */
	private List<CopNpc> responders(RadioCall call, double range, int limit) {
		Location     origin     = call.origin();
		NpcSquad     squad      = call.squad();
		List<CopNpc> candidates = new ArrayList<>();
		Map<CopNpc, Double> distances = new HashMap<>();

		for (CopGroup group : groups.values()) {
			if (group == call.group()) continue;
			synchronized (group.getCops()) {
				for (CopNpc cop : group.getCops()) {
					if (!cop.isValid() || cop.isMarkedForRemoval() || squad.members().contains(cop)) continue;
					if (!isFreeToRespond(cop)) continue;

					LivingEntity entity = cop.getEntity();
					if (entity == null || origin.getWorld() == null ||
					    !origin.getWorld().equals(entity.getWorld())) continue;
					double distSq = entity.getLocation().distanceSquared(origin);
					if (distSq > range * range) continue;

					candidates.add(cop);
					distances.put(cop, distSq);
				}
			}
		}

		candidates.sort(Comparator.comparingDouble(distances::get));
		return candidates.subList(0, Math.min(limit, candidates.size()));
	}

	/** Walking home, idle, or chasing a civilian; never cuffing, guarding or hunting a player. */
	private static boolean isFreeToRespond(CopNpc cop) {
		return switch (cop.getCurrentState()) {
			case RETURNING, IDLE -> true;
			case PURSUING, COMBAT -> cop.getTargetEntity() != null && cop.getTargetPlayerId() == null;
			default -> false;
		};
	}

	/**
	 * Resolves the current target for a cop. Priority order:
	 * <ol>
	 *   <li>Current wanted target — keep if still valid and wanted.</li>
	 *   <li>Current cop-attacker target — keep if still online and in the attacker registry.</li>
	 *   <li>Nearest wanted player in the world.</li>
	 *   <li>Nearest cop-attacker (combat mode) — someone who previously hit a cop.</li>
	 *   <li>Nearest wanted hostile civilian NPC.</li>
	 *   <li>No valid target — transition to RETURNING.</li>
	 * </ol>
	 *
	 * @param cop the cop NPC
	 * @param defaultTarget the reference player used to find the nearest wanted target
	 *
	 * @return the resolved target (Player or hostile civilian LivingEntity), or null if no target available
	 */
	private LivingEntity resolveTarget(CopNpc cop, Player defaultTarget) {
		// Check if cop is already pursuing a wanted civilian entity
		LivingEntity currentEntity = cop.getTargetEntity();
		if (currentEntity != null) {
			if (currentEntity.isValid() && !currentEntity.isDead()) {
				return currentEntity;
			}
			// Stale entity target — clear and fall through to player search
			cop.setTargetEntity(null);
			cop.setCombatForced(false);
		}

		UUID currentTargetId = cop.getTargetPlayerId();

		if (currentTargetId != null) {
			Player currentTarget = Bukkit.getPlayer(currentTargetId);

			if (currentTarget != null && currentTarget.isOnline() && !currentTarget.isDead() &&
			    !DownedPlayerRegistry.isDowned(currentTargetId)) {
				// A searched player is never fought: a cop still in COMBAT on him goes back to the chase (0.16.1 T-187)
				if (targetingManager.isSearching(currentTargetId) && cop.getCurrentState() == CopState.COMBAT) {
					cop.setCombatForced(false);
					cop.transitionTo(CopState.PURSUING);
				}

				// Keep if still wanted
				if (targetingManager.isWanted(currentTargetId)) {
					return currentTarget;
				}

				// Keep if this player attacked a cop (combat forced) — pursue even without wanted status
				if (cop.isCombatForced() && copAttackers.contains(currentTargetId)) {
					return currentTarget;
				}
			}

			// Stale target — clear and search for a new one
			cop.setTargetPlayerId(null);
			cop.setCombatForced(false);
		}

		// 1. Find the nearest wanted player (skip downed players)
		Player wanted = targetingManager.findBestTarget(defaultTarget);
		if (wanted != null && !wanted.isDead() && !DownedPlayerRegistry.isDowned(wanted.getUniqueId())) {
			cop.setTargetPlayerId(wanted.getUniqueId());
			cop.setCombatForced(false);
			// Leave combat mode so the cop pursues/cuffs normally instead of attacking
			if (cop.getCurrentState() == CopState.COMBAT) {
				cop.transitionTo(CopState.PURSUING);
			}
			return wanted;
		}

		// 2. Find the nearest player who previously attacked a cop (engage in combat)
		Player attacker = findNearestCopAttacker(cop);
		if (attacker != null) {
			cop.setTargetPlayerId(attacker.getUniqueId());
			cop.setTargetEntity(null);
			cop.setCombatForced(true);
			if (cop.getCurrentState() != CopState.COMBAT) {
				cop.transitionTo(CopState.COMBAT);
			}
			return attacker;
		}

		// 3. Find the nearest wanted hostile civilian NPC
		LivingEntity wantedCivilian = findNearestWantedCivilian(cop);
		if (wantedCivilian != null) {
			cop.setTargetEntity(wantedCivilian);
			cop.setTargetPlayerId(null);
			cop.setCombatForced(true);
			if (cop.getCurrentState() != CopState.COMBAT && cop.getCurrentState() != CopState.PURSUING) {
				cop.transitionTo(CopState.PURSUING);
			}
			return wantedCivilian;
		}

		// 4. Check entities that directly attacked this cop (self-defence, lowest priority)
		LivingEntity pendingAttacker = cop.pollNextEntityAttacker();
		if (pendingAttacker != null) {
			cop.setTargetEntity(pendingAttacker);
			cop.setTargetPlayerId(null);
			cop.setCombatForced(true);
			if (cop.getCurrentState() != CopState.COMBAT) {
				cop.transitionTo(CopState.COMBAT);
			}
			return pendingAttacker;
		}

		// No valid target — return to spawn
		cop.setTargetPlayerId(null);
		cop.setTargetEntity(null);
		cop.setCombatForced(false);
		if (cop.getCurrentState() != CopState.RETURNING && cop.getCurrentState() != CopState.IDLE) {
			cop.transitionTo(CopState.RETURNING);
		}
		return null;
	}

	/**
	 * Finds the nearest online player who has previously attacked a cop, relative to the cop's position.
	 *
	 * @param cop the cop NPC
	 *
	 * @return the nearest cop-attacker, or null
	 */
	private Player findNearestCopAttacker(CopNpc cop) {
		if (copAttackers.isEmpty() || !cop.isValid()) return null;

		LivingEntity entity = cop.getEntity();
		if (entity == null) return null;

		Location copLoc   = entity.getLocation();
		Player   best     = null;
		double   bestDist = Double.MAX_VALUE;

		for (UUID attackerId : copAttackers) {
			Player attacker = Bukkit.getPlayer(attackerId);
			if (attacker == null || !attacker.isOnline() || attacker.isDead() ||
			    DownedPlayerRegistry.isDowned(attackerId)) continue;
			if (!attacker.getWorld().equals(copLoc.getWorld())) continue;

			double dist = attacker.getLocation().distanceSquared(copLoc);
			if (dist >= bestDist) continue;

			bestDist = dist;
			best     = attacker;
		}

		return best;
	}

	/**
	 * Finds the nearest active hostile civilian NPC that is wanted by police (i.e. in combat), relative to the cop.
	 *
	 * @param cop the cop NPC
	 *
	 * @return the nearest wanted civilian entity, or null
	 */
	private LivingEntity findNearestWantedCivilian(CopNpc cop) {
		if (civilianNpcRegistry == null || !cop.isValid()) return null;

		LivingEntity copEntity = cop.getEntity();
		if (copEntity == null) return null;

		LivingEntity best     = null;
		double       bestDist = Double.MAX_VALUE;

		for (CivilianNpc civilian : civilianNpcRegistry.getActiveNpcs()) {
			if (!civilian.isHostile() || !civilian.isWantedByPolice() || !civilian.isValid()) continue;

			LivingEntity civEntity = civilian.getEntity();
			if (civEntity == null) continue;
			if (!civEntity.getWorld().equals(copEntity.getWorld())) continue;

			double dist = civEntity.getLocation().distanceSquared(copEntity.getLocation());
			if (dist >= bestDist) continue;

			bestDist = dist;
			best     = civEntity;
		}

		return best;
	}

	/**
	 * Stops the AI task for a player.
	 *
	 * @param playerId the player UUID
	 */
	private void stopAITask(UUID playerId) {
		BukkitTask task = aiTasks.remove(playerId);
		if (task != null) task.cancel();
	}

	/**
	 * Despawns and removes all cops assigned to a player.
	 *
	 * @param playerId the player UUID
	 */
	private void despawnAllForPlayer(UUID playerId) {
		CopGroup group = groups.remove(playerId);
		if (group == null) return;

		fieldCare.clear(group);
		group.destroyAll(markManager);
	}
}