package me.luckyraven.feature.wanted;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import me.luckyraven.util.timer.RepeatingTimer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

@Data
public class Wanted {

	private int level;
	@Setter(AccessLevel.NONE)
	private int increments;

	private int     maxLevel;
	private boolean wanted;

	@Setter(AccessLevel.NONE)
	private RepeatingTimer repeatingTimer;

	public Wanted(int increments, int maxLevel) {
		this.level      = 0;
		this.increments = increments;
		this.maxLevel   = maxLevel;
		this.wanted     = false;
	}

	public RepeatingTimer createTimer(JavaPlugin plugin, long seconds, Consumer<RepeatingTimer> timer) {
		if (repeatingTimer != null) {
			repeatingTimer.stop();
			repeatingTimer = null;
		}

		this.repeatingTimer = new RepeatingTimer(plugin, seconds * 20L, timer);

		return repeatingTimer;
	}

	public void setLevel(int level) {
		this.level  = Math.max(0, Math.min(level, maxLevel));
		this.wanted = this.level > 0;
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

		if (this.repeatingTimer != null) {
			this.repeatingTimer.stop();
		}
	}

}
