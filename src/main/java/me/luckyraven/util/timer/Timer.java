package me.luckyraven.util.timer;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public abstract class Timer extends BukkitRunnable {

	private final         JavaPlugin plugin;
	private final @Getter long       delay, period;

	private          BukkitTask bukkitTask;
	private volatile boolean    stopped;
	private @Getter  long       interval;

	public Timer(JavaPlugin plugin) {
		this(plugin, 0L, 20L);
	}

	public Timer(JavaPlugin plugin, long delay, long period) {
		this.plugin = plugin;
		this.delay = delay;
		this.period = period;
		this.stopped = true;
	}

	public boolean isRunning() {
		return !stopped;
	}

	public void start(boolean async) {
		if (bukkitTask != null && !stopped) return;

		if (async) this.bukkitTask = runTaskTimerAsynchronously(plugin, delay, period);
		else this.bukkitTask = runTaskTimer(plugin, delay, period);

		stopped = false;
	}

	public void stop() {
		if (stopped || bukkitTask == null) return;

		this.bukkitTask.cancel();

		this.stopped = true;
	}

}
