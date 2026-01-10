package me.luckyraven.copsncrooks.wanted;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import me.luckyraven.util.timer.RepeatingTimer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

@Data
public class Wanted {

	private int level;
	@Setter(AccessLevel.NONE)
	private int increments;

	private int     maxLevel;
	private boolean wanted;
	private Player  owner;

	@Setter(AccessLevel.NONE)
	private RepeatingTimer repeatingTimer;

	public Wanted(int increments, int maxLevel) {
		this.level      = 0;
		this.increments = increments;
		this.maxLevel   = maxLevel;
		this.wanted     = false;
	}

	public RepeatingTimer createTimer(JavaPlugin plugin, long seconds, Consumer<RepeatingTimer> timer) {
		stopTimer();

		this.repeatingTimer = new RepeatingTimer(plugin, seconds * 20L, timer);

		return repeatingTimer;
	}

	public void setLevel(int level) {
		int oldLevel = this.level;
		int newLevel = Math.max(0, Math.min(level, maxLevel));

		// Fire change event if owner is set
		if (owner != null && oldLevel != newLevel) {
			WantedLevelChangeEvent changeEvent = new WantedLevelChangeEvent(owner, this, oldLevel, newLevel);
			Bukkit.getPluginManager().callEvent(changeEvent);

			if (changeEvent.isCancelled()) return;
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
		StringBuilder builder = new StringBuilder(maxLevel);

		builder.append("★".repeat(level)).append("☆".repeat(Math.max(0, maxLevel - builder.length())));

		return builder.toString();
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
