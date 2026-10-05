package org.luckyraven.gangland.core.wanted;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.timer.RepeatingTimer;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.core.events.wanted.WantedStartEvent;

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
		int filled    = Math.max(0, Math.min(level, cappedMax));
		int empty     = cappedMax - filled;

		return "★".repeat(filled) + "☆".repeat(empty);
	}

	public RepeatingTimer createTimer(long seconds, Consumer<RepeatingTimer> timer) {
		stopTimer();

		this.repeatingTimer = new RepeatingTimer(plugin, seconds * 20L, timer);

		return repeatingTimer;
	}

	public void setLevel(int level) {
		setLevel(level, WantedCause.UNKNOWN);
	}

	/**
	 * The single choke point every star change goes through. Fires {@link WantedLevelChangeEvent} (cancellable, BEFORE
	 * the level mutates) and then {@link WantedStartEvent} / {@link WantedEndEvent}, all carrying {@code cause}, when an
	 * owner is set. Off the main thread with an owner it re-schedules itself there and returns.
	 */
	public void setLevel(int level, WantedCause cause) {
		int oldLevel = this.level;
		int newLevel = Math.max(0, Math.min(level, maxLevel));

		// Fire change event if owner is set
		if (owner != null && oldLevel != newLevel) {
			WantedLevelChangeEvent changeEvent = new WantedLevelChangeEvent(owner, this, oldLevel, newLevel, cause);

			// Must call event synchronously
			if (Bukkit.isPrimaryThread()) {
				Bukkit.getPluginManager().callEvent(changeEvent);
				if (changeEvent.isCancelled()) return;
			} else {
				// Schedule sync and return - the sync task will handle the level change
				Bukkit.getScheduler().runTask(plugin, () -> setLevel(level, cause));
				return;
			}
		}

		boolean wasWanted = this.wanted;
		this.level  = newLevel;
		this.wanted = this.level > 0;

		// Fire start/end events
		if (owner != null) {
			if (!wasWanted && this.wanted) {
				Bukkit.getPluginManager().callEvent(new WantedStartEvent(owner, this, this.level, cause));
			} else if (wasWanted && !this.wanted) {
				Bukkit.getPluginManager().callEvent(new WantedEndEvent(owner, this, cause));
			}
		}
	}

	public void incrementLevel() {
		incrementLevel(WantedCause.UNKNOWN);
	}

	public void incrementLevel(WantedCause cause) {
		setLevel(level + increments, cause);
	}

	public void decrementLevel() {
		decrementLevel(WantedCause.UNKNOWN);
	}

	public void decrementLevel(WantedCause cause) {
		setLevel(level - 1, cause);
	}

	public String getLevelStars() {
		return buildStars(level, maxLevel);
	}

	public void reset() {
		reset(WantedCause.UNKNOWN);
	}

	public void reset(WantedCause cause) {
		setLevel(0, cause);
		stopTimer();
	}

	public void stopTimer() {
		if (this.repeatingTimer == null) return;

		this.repeatingTimer.stop();
		this.repeatingTimer = null;
	}

}
