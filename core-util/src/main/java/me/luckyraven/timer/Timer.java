package me.luckyraven.timer;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Timer extends BukkitRunnable {

	private final JavaPlugin plugin;
	@Getter
	private final long       delay, period;
	private final AtomicBoolean stopped;

	private BukkitTask bukkitTask;

	public Timer(JavaPlugin plugin) {
		this(plugin, 0L, 20L);
	}

	public Timer(JavaPlugin plugin, long delay, long period) {
		this.plugin  = plugin;
		this.delay   = delay;
		this.period  = period;
		this.stopped = new AtomicBoolean(true);
	}

	protected abstract void onStop();

	public boolean isRunning() {
		return !stopped.get();
	}

	public void start(boolean async) {
		if (bukkitTask != null) return;
		if (isRunning()) return;

		if (async) this.bukkitTask = runTaskTimerAsynchronously(plugin, delay, period);
		else this.bukkitTask = runTaskTimer(plugin, delay, period);

		this.stopped.set(false);
	}

	public void stop() {
		if (!isRunning() || bukkitTask == null) return;

		this.bukkitTask.cancel();
		this.stopped.set(true);

		onStop();

		this.bukkitTask = null;
	}

}
