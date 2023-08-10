package me.luckyraven.timer;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Consumer;

public class RepeatingTimer extends BukkitRunnable {

	private final JavaPlugin               plugin;
	private final Consumer<RepeatingTimer> repeatingTask;

	private BukkitTask bukkitTask;
	@Getter
	private long       interval;
	private boolean    stopped;

	public RepeatingTimer(JavaPlugin plugin, long interval, Consumer<RepeatingTimer> repeatingTask) {
		this.plugin = plugin;
		this.interval = interval;
		this.repeatingTask = repeatingTask;
		this.stopped = true;
	}

	@Override
	public void run() {
		if (!stopped) repeatingTask.accept(this);
		else cancel();
	}

	public boolean isRunning() {
		return !stopped;
	}

	public void setInterval(long interval) {
		this.interval = interval;

		if (bukkitTask == null || stopped) return;

		stop();
		start();
	}

	public void stop() {
		if (stopped) return;
		this.bukkitTask.cancel();
		this.stopped = true;
	}

	public void start() {
		if (bukkitTask != null && !stopped) return;
		this.bukkitTask = runTaskTimer(plugin, 0L, interval);
		stopped = false;
	}

	public void startAsync() {
		if (bukkitTask != null && !stopped) return;
		this.bukkitTask = runTaskTimerAsynchronously(plugin, 0L, interval);
		stopped = false;
	}

}
