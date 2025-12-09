package me.luckyraven.feature.combo;

import lombok.Getter;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.timer.CountdownTimer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tracks kill combo data for a specific player.
 */
@Getter
public class KillComboTracker {

	private final JavaPlugin                 plugin;
	private final Player                     player;
	private final Consumer<KillComboTracker> onReset;
	private final List<KillRecord>           killHistory;

	private int            normalKillCount;
	private int            pointKillCount;
	private CountdownTimer timer;

	public KillComboTracker(JavaPlugin plugin, Player player, Consumer<KillComboTracker> onReset) {
		this.plugin          = plugin;
		this.player          = player;
		this.onReset         = onReset;
		this.normalKillCount = 0;
		this.pointKillCount  = 0;
		this.killHistory     = new ArrayList<>();

		// Initialize countdown timer
		initializeTimer();
	}

	/**
	 * Adds a kill to the combo.
	 *
	 * @param killed The entity that was killed
	 * @param points The points awarded for this kill
	 */
	public void addKill(Entity killed, int points) {
		normalKillCount++;
		pointKillCount += points;

		KillRecord record = new KillRecord(killed.getType(), points, System.currentTimeMillis());
		killHistory.add(record);
	}

	public void restartTimer() {
		if (timer != null) {
			timer.stop();
			timer = null;
		}

		initializeTimer();
		timer.start(true);
	}

	public void stopTimer() {
		if (!(timer != null && timer.isRunning())) return;

		timer.stop();
	}

	/**
	 * Gets the remaining time on the combo timer in seconds.
	 */
	public long getRemainingTime() {
		return timer != null ? timer.getTimeLeft() : 0;
	}

	private void initializeTimer() {
		this.timer = new CountdownTimer(plugin, SettingAddon.getWantedKillComboResetAfter(), null, null, timer -> {
			if (onReset == null) return;

			onReset.accept(this);
		});
	}

	public record KillRecord(EntityType entityType, int points, long timestamp) { }

}
