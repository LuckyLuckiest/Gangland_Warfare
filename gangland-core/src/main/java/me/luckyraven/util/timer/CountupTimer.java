package me.luckyraven.util.timer;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

public class CountupTimer extends Timer {

	private final Consumer<CountupTimer> duringTimer, beforeTimer;

	@Getter
	private long elapsedTime;

	public CountupTimer(JavaPlugin plugin) {
		this(plugin, null);
	}

	public CountupTimer(JavaPlugin plugin, Consumer<CountupTimer> duringTimer) {
		this(plugin, null, duringTimer);
	}

	public CountupTimer(JavaPlugin plugin, Consumer<CountupTimer> beforeTimer, Consumer<CountupTimer> duringTimer) {
		this(plugin, 0L, beforeTimer, duringTimer);
	}

	public CountupTimer(JavaPlugin plugin, long delay, Consumer<CountupTimer> beforeTimer,
						Consumer<CountupTimer> duringTimer) {
		// By default, it would run for an interval of 20L, thus every second
		this(plugin, delay, 20L, beforeTimer, duringTimer);
	}

	public CountupTimer(JavaPlugin plugin, long delay, long period) {
		this(plugin, delay, period, null, null);
	}

	public CountupTimer(JavaPlugin plugin, long delay, long period, Consumer<CountupTimer> beforeTimer,
						Consumer<CountupTimer> duringTimer) {
		super(plugin, delay, period);

		this.elapsedTime = 0L;
		this.beforeTimer = beforeTimer;
		this.duringTimer = duringTimer;
	}

	@Override
	public void run() {
		if (beforeTimer != null && elapsedTime == 0L) beforeTimer.accept(this);
		if (duringTimer != null) duringTimer.accept(this);

		++elapsedTime;
	}

	public void reset() {
		elapsedTime = 0L;
	}

}
