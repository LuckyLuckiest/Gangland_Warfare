package me.luckyraven.copsncrooks.wanted;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import me.luckyraven.copsncrooks.events.wanted.WantedEndEvent;
import me.luckyraven.copsncrooks.events.wanted.WantedLevelChangeEvent;
import me.luckyraven.copsncrooks.events.wanted.WantedStartEvent;
import me.luckyraven.core.timer.RepeatingTimer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

@Data
public class Wanted {

	@Getter(AccessLevel.NONE)
	private final JavaPlugin plugin;

	private int level;
	@Setter(AccessLevel.NONE)
	private int increments;

	private int     maxLevel;
	private boolean wanted;
	private Player  owner;

	@Setter(AccessLevel.NONE)
	private RepeatingTimer repeatingTimer;

	public Wanted(JavaPlugin plugin, int increments, int maxLevel) {
		this.plugin = plugin;

		this.level      = 0;
		this.increments = increments;
		this.maxLevel   = maxLevel;
		this.wanted     = false;
	}

	public static String buildStars(int level, int maxLevel) {
		int cappedMax = Math.max(0, maxLevel);
		int filled    = Math.clamp(level, 0, cappedMax);
		int empty     = cappedMax - filled;

		return "★".repeat(filled) + "☆".repeat(empty);
	}

	public RepeatingTimer createTimer(long seconds, Consumer<RepeatingTimer> timer) {
		stopTimer();

		this.repeatingTimer = new RepeatingTimer(plugin, seconds * 20L, timer);

		return repeatingTimer;
	}

	public void setLevel(int level) {
		int oldLevel = this.level;
		int newLevel = Math.clamp(level, 0, maxLevel);

		// Fire change event if owner is set
		if (owner != null && oldLevel != newLevel) {
			WantedLevelChangeEvent changeEvent = new WantedLevelChangeEvent(owner, this, oldLevel, newLevel);

			// Must call event synchronously
			if (Bukkit.isPrimaryThread()) {
				Bukkit.getPluginManager().callEvent(changeEvent);
				if (changeEvent.isCancelled()) return;
			} else {
				// Schedule sync and return - the sync task will handle the level change
				Bukkit.getScheduler().runTask(plugin, () -> setLevel(level));
				return;
			}
		}

		boolean wasWanted = this.wanted;
		this.level  = newLevel;
		this.wanted = this.level > 0;

		// Fire start/end events
		if (owner != null) {
			if (!wasWanted && this.wanted) {
				Bukkit.getPluginManager().callEvent(new WantedStartEvent(owner, this, this.level));
			} else if (wasWanted && !this.wanted) {
				Bukkit.getPluginManager().callEvent(new WantedEndEvent(owner, this));
			}
		}
	}

	public void incrementLevel() {
		setLevel(increments + level);
	}

	public void decrementLevel() {
		setLevel(level - 1);
	}

	public String getLevelStars() {
		return buildStars(level, maxLevel);
	}

	public void reset() {
		setLevel(0);
		stopTimer();
	}

	public void stopTimer() {
		if (this.repeatingTimer == null) return;

		this.repeatingTimer.stop();
		this.repeatingTimer = null;
	}

}
