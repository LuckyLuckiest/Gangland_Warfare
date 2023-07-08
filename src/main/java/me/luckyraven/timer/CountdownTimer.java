package me.luckyraven.timer;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Consumer;

public class CountdownTimer extends BukkitRunnable {

	private final JavaPlugin               plugin;
	private final Consumer<CountdownTimer> duringTimer, beforeTimer, afterTimer;
	@Getter
	private final int duration;

	@Getter
	private int        timeLeft;
	private BukkitTask bukkitTask;

	public CountdownTimer(JavaPlugin plugin, int duration) {
		this(plugin, duration, null);
	}

	public CountdownTimer(JavaPlugin plugin, int duration, Consumer<CountdownTimer> duringTimer) {
		this(plugin, duration, null, duringTimer, null);
	}

	public CountdownTimer(JavaPlugin plugin, int duration, Consumer<CountdownTimer> beforeTimer,
	                      Consumer<CountdownTimer> duringTimer, Consumer<CountdownTimer> afterTimer) {
		this.plugin = plugin;
		this.duration = duration;
		this.timeLeft = duration;
		this.beforeTimer = beforeTimer;
		this.duringTimer = duringTimer;
		this.afterTimer = afterTimer;
	}

	@Override
	public void run() {
		if (timeLeft < 1) {
			afterTimer.accept(this);
			if (bukkitTask != null) cancel();
			return;
		}

		if (beforeTimer != null && timeLeft == duration) beforeTimer.accept(this);
		if (duringTimer != null) duringTimer.accept(this);

		timeLeft--;
	}

	public void start() {
		this.bukkitTask = runTaskTimer(plugin, 0L, 20L);
	}

}
