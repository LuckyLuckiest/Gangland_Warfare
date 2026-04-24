package me.luckyraven.copsncrooks.npc.turf.defender;

import lombok.CustomLog;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.civilian.CivilianState;
import me.luckyraven.copsncrooks.npc.civilian.npc.CivilianNpc;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.function.Supplier;

/**
 * Spawns and ticks ephemeral turf defenders during a capture by reusing the existing civilian NPC infrastructure. No
 * bespoke entity types — every defender is a {@link CivilianNpc} of the configured type id from {@code civilians.yml},
 * transitioned into {@link CivilianState#COMBAT} with its target pointed at whichever online attacker is closest. The
 * civilian system already handles pathing, attack cadence, and entity cleanup; this deployer just owns the spawn /
 * target-rebind / recall lifecycle bound to a specific turf.
 *
 * <p>One {@link Group} per turf — multiple deploys onto the same turf stack into the same group so the
 * targeting tick runs once per turf instead of once per NPC. The 5-tick AI task scans each defender's nearby players,
 * picks the closest live challenger gang member within {@code targetingRadius}, sets it as the civilian's combat
 * target, and forces a transition into COMBAT if not already there.
 */
@CustomLog
public final class TurfDefenderDeployer {

	private static final long TICK_PERIOD = 5L;

	private final JavaPlugin           plugin;
	private final CivilianService      civilianService;
	private final CivilianSpawnManager spawnManager;

	private final Map<Integer, Group> byTurfId = new HashMap<>();
	private       BukkitTask          tickTask;

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

	public void stop() {
		if (tickTask != null) {
			tickTask.cancel();
			tickTask = null;
		}
		for (Group g : byTurfId.values()) g.recallAll();
		byTurfId.clear();
	}

	/**
	 * Deploy {@code count} civilian defenders of the given type at {@code spawnLocation}, hostile to whichever players
	 * the supplier names (re-evaluated on every targeting tick so a fresh log-in by an attacker is picked up). Stacks
	 * onto the existing group if one is already deployed for this turf.
	 *
	 * @param turfId turf-scoped key for grouping + recall
	 * @param spawnLocation where the defenders appear
	 * @param civilianTypeId the {@code civilians.yml} type id to spawn (e.g. {@code "turf_defender"})
	 * @param challengerMembersSupplier resolves the set of player UUIDs the defenders should attack
	 * @param count how many defenders to spawn
	 * @param targetingRadius defenders only target players within this distance (blocks)
	 * @param lifespanSeconds safety lifespan — defender is recalled if it outlives this regardless of contest state
	 */
	public void deploy(int turfId,
	                   Location spawnLocation,
	                   String civilianTypeId,
	                   Supplier<Set<UUID>> challengerMembersSupplier,
	                   int count,
	                   double targetingRadius,
	                   int lifespanSeconds) {
		if (count <= 0) return;

		Group group = byTurfId.computeIfAbsent(turfId,
		                                       k -> new Group(challengerMembersSupplier, targetingRadius));
		group.challengerMembers = challengerMembersSupplier;
		group.targetingRadius   = targetingRadius;
		long expiresAt = System.currentTimeMillis() + lifespanSeconds * 1000L;
		for (int i = 0; i < count; i++) {
			CivilianNpc npc = spawnManager.spawnCivilian(spawnLocation, civilianTypeId);
			if (npc == null) {
				log.warn("Failed to spawn turf defender of type '{}' at {} (CivilianSpawnManager returned null)",
				         civilianTypeId, spawnLocation);
				continue;
			}
			group.defenders.add(new TrackedDefender(npc, expiresAt));
		}
	}

	/**
	 * Recall every defender deployed for {@code turfId}. Called on capture-complete and capture-failed so a finished
	 * contest cleans up its garrison even if some defenders are still alive.
	 */
	public void recall(int turfId) {
		Group group = byTurfId.remove(turfId);
		if (group != null) group.recallAll();
	}

	/**
	 * Returns the turf id this entity is a defender for, or {@code -1} if the entity is not a tracked defender. Used by
	 * the friendly-fire listener to short-circuit damage from the owning gang.
	 */
	public int findOwningTurfId(Entity entity) {
		if (entity == null) return -1;
		for (Map.Entry<Integer, Group> entry : byTurfId.entrySet()) {
			for (TrackedDefender d : entry.getValue().defenders) {
				if (d.npc.isValid() && entity.equals(d.npc.getEntity())) {
					return entry.getKey();
				}
			}
		}
		return -1;
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

	private void tick() {
		long now = System.currentTimeMillis();

		for (Iterator<Map.Entry<Integer, Group>> entries = byTurfId.entrySet().iterator(); entries.hasNext(); ) {
			Map.Entry<Integer, Group> entry = entries.next();
			Group                     group = entry.getValue();

			// Reap dead / expired defenders first so targeting only runs over live ones.
			for (Iterator<TrackedDefender> it = group.defenders.iterator(); it.hasNext(); ) {
				TrackedDefender d = it.next();
				if (!d.npc.isValid() || d.npc.isMarkedForRemoval() || now >= d.expiresAt) {
					if (d.npc.isValid()) d.npc.markForRemoval();
					it.remove();
				}
			}
			if (group.defenders.isEmpty()) {
				entries.remove();
				continue;
			}

			Set<UUID> challengerIds = group.challengerMembers.get();
			if (challengerIds == null || challengerIds.isEmpty()) continue;

			double radiusSquared = group.targetingRadius * group.targetingRadius;
			for (TrackedDefender d : group.defenders) {
				retarget(d.npc, challengerIds, radiusSquared);
			}
		}
	}

	private void retarget(CivilianNpc npc, Set<UUID> challengerIds, double radiusSquared) {
		if (npc.getEntity() == null) return;
		Player closest     = null;
		double closestDist = radiusSquared;
		for (Player player : npc.getEntity().getWorld().getPlayers()) {
			if (!challengerIds.contains(player.getUniqueId())) continue;
			if (player.isDead()) continue;
			double dist = player.getLocation().distanceSquared(npc.getEntity().getLocation());
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
		Supplier<Set<UUID>> challengerMembers;
		double              targetingRadius;

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

	private record TrackedDefender(CivilianNpc npc, long expiresAt) {
	}
}
