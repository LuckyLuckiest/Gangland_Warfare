package org.luckyraven.gangland.copsncrooks.combo;

import lombok.Setter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.events.combo.KillComboEvent;
import org.luckyraven.keystone.util.NumberUtil;
import org.luckyraven.gangland.gang.wanted.Wanted;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Setter
public class KillCombo {

	private final JavaPlugin                  plugin;
	private final Map<UUID, KillComboTracker> activeTrackers;
	private final List<Integer>               wantedKillCounter;

	private Consumer<KillComboEvent> onWantedLevelTrigger;
	private Consumer<KillComboEvent> onComboIncrement;
	private Consumer<KillComboEvent> onComboReset;
	private Consumer<UUID>           onPlayerDeath;

	public KillCombo(JavaPlugin plugin, List<Integer> wantedKillCounter) {
		this.plugin            = plugin;
		this.activeTrackers    = new HashMap<>();
		this.wantedKillCounter = wantedKillCounter;
	}

	/**
	 * Records a kill for the given player.
	 *
	 * @param killer The player who made the kill
	 * @param killed The entity that was killed
	 */
	public void recordKill(Player killer, Wanted wantedKiller, Entity killed, int wantedKillComboResetAfter) {
		UUID playerId = killer.getUniqueId();

		var killComboTracker = new KillComboTracker(plugin, killer, this::handleComboReset, wantedKillComboResetAfter);
		var tracker          = activeTrackers.computeIfAbsent(playerId, id -> killComboTracker);

		// Determine kill points based on entity type
		int points = 1;

		// Increment the combo
		tracker.addKill(killed, points);

		// Trigger combo increment callback
		if (onComboIncrement != null) {
			KillComboEvent event = new KillComboEvent(killer, tracker);
			onComboIncrement.accept(event);
		}

		// Check if wanted level should be triggered
		checkWantedLevelTrigger(killer, wantedKiller, tracker);

		// Restart the countdown timer
		tracker.restartTimer();
	}

	/**
	 * Handles a player's death by resetting their combo. This callback should be triggered when a player dies.
	 *
	 * @param playerId The UUID of the player who died
	 */
	public void handlePlayerDeath(UUID playerId) {
		resetCombo(playerId);

		if (onPlayerDeath == null) return;

		onPlayerDeath.accept(playerId);
	}

	/**
	 * Resets the kill combo for the given player.
	 *
	 * @param playerId The UUID of the player
	 */
	public void resetCombo(UUID playerId) {
		KillComboTracker tracker = activeTrackers.remove(playerId);

		if (tracker == null) return;

		tracker.stopTimer();
		handleComboReset(tracker);
	}

	/**
	 * Gets the current kill combo tracker for a player.
	 *
	 * @param playerId The UUID of the player
	 *
	 * @return The tracker, or null if none exists
	 */
	public KillComboTracker getTracker(UUID playerId) {
		return activeTrackers.get(playerId);
	}

	/**
	 * Clears all active trackers.
	 */
	public void clearAll() {
		activeTrackers.values().forEach(KillComboTracker::stopTimer);
		activeTrackers.clear();
	}

	/**
	 * Checks if the combo should trigger a wanted level increase.
	 */
	private void checkWantedLevelTrigger(Player killer, Wanted wantedKiller, KillComboTracker tracker) {
		int pointKillCount = tracker.getPointKillCount();

		// Check against configured thresholds
		if (!shouldTriggerWantedLevel(wantedKiller, pointKillCount)) return;
		if (onWantedLevelTrigger == null) return;

		KillComboEvent event = new KillComboEvent(killer, tracker);
		onWantedLevelTrigger.accept(event);
	}

	private boolean shouldTriggerWantedLevel(Wanted wanted, int pointsKillCount) {
		List<Integer> thresholds = wantedKillCounter;

		if (thresholds.isEmpty()) return false;
		if (thresholds.size() < wanted.getMaxLevel()) {
			// create a linear list of thresholds if not enough thresholds are configured
			thresholds = NumberUtil.resizeLinear(thresholds, wanted.getMaxLevel());
		}

		int level = Math.min(wanted.getLevel(), thresholds.size() - 1);

		return pointsKillCount >= thresholds.get(level);
	}

	/**
	 * Handles combo reset callback.
	 */
	private void handleComboReset(KillComboTracker tracker) {
		activeTrackers.remove(tracker.getPlayer().getUniqueId());

		if (onComboReset == null) return;

		KillComboEvent event = new KillComboEvent(tracker.getPlayer(), tracker);
		onComboReset.accept(event);
	}

}
