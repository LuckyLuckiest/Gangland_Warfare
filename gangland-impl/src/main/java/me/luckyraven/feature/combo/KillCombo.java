package me.luckyraven.feature.combo;

import lombok.Setter;
import me.luckyraven.data.user.User;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Setter
public class KillCombo {

	private final JavaPlugin                  plugin;
	private final Map<UUID, KillComboTracker> activeTrackers;

	private Consumer<KillComboEvent> onWantedLevelTrigger;
	private Consumer<KillComboEvent> onComboIncrement;
	private Consumer<KillComboEvent> onComboReset;

	public KillCombo(JavaPlugin plugin) {
		this.plugin         = plugin;
		this.activeTrackers = new HashMap<>();
	}

	/**
	 * Records a kill for the given player.
	 *
	 * @param killer The player who made the kill
	 * @param killed The entity that was killed
	 */
	public void recordKill(User<Player> killer, Entity killed) {
		Player player   = killer.getUser();
		UUID   playerId = player.getUniqueId();

		var killComboTracker = new KillComboTracker(plugin, player, this::handleComboReset);
		var tracker          = activeTrackers.computeIfAbsent(playerId, id -> killComboTracker);

		// Determine kill points based on entity type
		int points = 1;

		// Increment the combo
		tracker.addKill(killed, points);

		// Trigger combo increment callback
		if (onComboIncrement != null) {
			KillComboEvent event = new KillComboEvent(player, tracker);
			onComboIncrement.accept(event);
		}

		// Check if wanted level should be triggered
		checkWantedLevelTrigger(killer, tracker);

		// Restart the countdown timer
		tracker.restartTimer();
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
	private void checkWantedLevelTrigger(User<Player> killer, KillComboTracker tracker) {
		int pointKillCount = tracker.getPointKillCount();

		// Check against configured thresholds
		if (!shouldTriggerWantedLevel(pointKillCount)) return;
		if (onWantedLevelTrigger == null) return;

		KillComboEvent event = new KillComboEvent(killer.getUser(), tracker);
		onWantedLevelTrigger.accept(event);
	}

	private boolean shouldTriggerWantedLevel(int pointsKillCount) {
		List<Integer> thresholds = SettingAddon.getWantedKillCounter();

		for (int threshold : thresholds) {
			if (pointsKillCount >= threshold) return true;
		}

		return false;
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
