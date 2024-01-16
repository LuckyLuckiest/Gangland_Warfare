package me.luckyraven.util.timer;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

public class CountdownTimer extends Timer {

	private final Consumer<CountdownTimer> duringTimer, beforeTimer, afterTimer;

	private @Getter long timeLeft;

	public CountdownTimer(JavaPlugin plugin, long time) {
		this(plugin, time, null);
	}

	public CountdownTimer(JavaPlugin plugin, long time, Consumer<CountdownTimer> duringTimer) {
		this(plugin, time, null, duringTimer, null);
	}

	public CountdownTimer(JavaPlugin plugin, long time, Consumer<CountdownTimer> beforeTimer,
						  Consumer<CountdownTimer> duringTimer, Consumer<CountdownTimer> afterTimer) {
		this(plugin, 0L, time, beforeTimer, duringTimer, afterTimer);
	}

	public CountdownTimer(JavaPlugin plugin, long delay, long time, Consumer<CountdownTimer> beforeTimer,
						  Consumer<CountdownTimer> duringTimer, Consumer<CountdownTimer> afterTimer) {
		// By default, it would run for an interval of 20L, thus every second
		this(plugin, delay, 20L, time, beforeTimer, duringTimer, afterTimer);
	}

	public CountdownTimer(JavaPlugin plugin, long delay, long period, long time, Consumer<CountdownTimer> beforeTimer,
						  Consumer<CountdownTimer> duringTimer, Consumer<CountdownTimer> afterTimer) {
		super(plugin, delay, period);
		this.timeLeft = time;
		this.beforeTimer = beforeTimer;
		this.duringTimer = duringTimer;
		this.afterTimer = afterTimer;
	}

	@Override
	public void run() {
		if (timeLeft < 1) {
			afterTimer.accept(this);
			stop();
			return;
		}

		if (beforeTimer != null && timeLeft == getPeriod()) beforeTimer.accept(this);
		if (duringTimer != null) duringTimer.accept(this);

		--timeLeft;
	}

}
