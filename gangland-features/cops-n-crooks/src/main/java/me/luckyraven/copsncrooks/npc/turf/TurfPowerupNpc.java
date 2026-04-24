package me.luckyraven.copsncrooks.npc.turf;

import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.copsncrooks.npc.civilian.CivilianState;
import me.luckyraven.copsncrooks.npc.civilian.npc.CivilianNpc;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import me.luckyraven.copsncrooks.npc.turf.config.TurfPowerupSettings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Per-turf Quartermaster NPC. Spawned as a {@link CivilianNpc} of the configured {@code civilians.yml} type id — so its
 * model, equipment, health, attack damage, and combat AI all come from the civilian system, not from bespoke code.
 * Right-click panel access is layered on top by tagging the underlying Citizens NPC with the turf-id metadata; the
 * interact listener uses that metadata to route the click into the panel flow.
 *
 * <p>While the turf is being contested, {@link #engage(Supplier)} sets the civilian's combat target to whichever
 * online attacker is closest (re-evaluated every targeting tick) and forces a transition into
 * {@link CivilianState#COMBAT}. {@link #disengage()} clears the target and returns to IDLE when the contest ends.
 * Without these calls the Quartermaster sits idle in its civilian type's normal IDLE behaviour.
 */
@CustomLog
public final class TurfPowerupNpc {

	public static final String METADATA_TURF_ID = "gangland.turfpowerup.turfid";

	private static final long TARGET_TICK_PERIOD = 5L;

	@Getter
	private final TurfPowerupData data;
	@Getter
	private final CivilianNpc     civilian;

	private final JavaPlugin plugin;

	private Supplier<Set<UUID>> attackerSupplier;
	private double              targetingRadius = 32.0;
	private BukkitTask          targetingTask;

	public TurfPowerupNpc(JavaPlugin plugin, TurfPowerupData data, CivilianNpc civilian) {
		this.plugin   = plugin;
		this.data     = data;
		this.civilian = civilian;
	}

	public static @Nullable TurfPowerupNpc spawn(JavaPlugin plugin,
	                                             TurfPowerupData data,
	                                             TurfPowerupSettings settings,
	                                             CivilianSpawnManager spawnManager) {
		CivilianNpc civilian = spawnManager.spawnCivilian(data.getSpawnLocation(), settings.typeId());
		if (civilian == null) {
			log.warn("CivilianSpawnManager returned null for Quartermaster type '{}' at {} — turf {} has no NPC",
			         settings.typeId(), data.getSpawnLocation(), data.getTurfId());
			return null;
		}
		civilian.getNpc().data().setPersistent(METADATA_TURF_ID, data.getTurfId());
		return new TurfPowerupNpc(plugin, data, civilian);
	}

	public boolean isAlive() {
		return civilian.isValid() && !civilian.isMarkedForRemoval();
	}

	public void destroy() {
		stopTargetingTask();
		if (civilian.isValid()) civilian.markForRemoval();
	}

	/**
	 * Bind the Quartermaster to a contest: target whoever the supplier names (re-evaluated every targeting tick) and
	 * force a transition into COMBAT so the civilian's attack pipeline takes over.
	 */
	public void engage(Supplier<Set<UUID>> attackerSupplier, double targetingRadius) {
		this.attackerSupplier = attackerSupplier;
		this.targetingRadius  = targetingRadius;
		startTargetingTask();
	}

	public void disengage() {
		stopTargetingTask();
		if (civilian.isValid()) {
			civilian.setTargetPlayerId(null);
			civilian.transitionTo(CivilianState.IDLE);
		}
	}

	private void startTargetingTask() {
		if (targetingTask != null) return;
		targetingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::retarget,
		                                                   TARGET_TICK_PERIOD, TARGET_TICK_PERIOD);
	}

	private void stopTargetingTask() {
		if (targetingTask != null) {
			targetingTask.cancel();
			targetingTask = null;
		}
	}

	private void retarget() {
		if (!isAlive() || attackerSupplier == null) return;
		Set<UUID> attackers = attackerSupplier.get();
		if (attackers == null || attackers.isEmpty()) {
			civilian.setTargetPlayerId(null);
			return;
		}
		Entity entity = civilian.getEntity();
		if (entity == null) return;

		double radiusSquared = targetingRadius * targetingRadius;
		Player closest       = null;
		double closestDist   = radiusSquared;
		for (Player player : entity.getWorld().getPlayers()) {
			if (!attackers.contains(player.getUniqueId())) continue;
			if (player.isDead()) continue;
			double dist = player.getLocation().distanceSquared(entity.getLocation());
			if (dist < closestDist) {
				closest     = player;
				closestDist = dist;
			}
		}
		if (closest != null) {
			civilian.setTargetPlayerId(closest.getUniqueId());
			if (civilian.getCurrentState() != CivilianState.COMBAT) {
				civilian.transitionTo(CivilianState.COMBAT);
			}
		}
	}
}
