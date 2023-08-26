package me.luckyraven.util.timer;

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
	private boolean    async;

	public RepeatingTimer(JavaPlugin plugin, long interval, Consumer<RepeatingTimer> repeatingTask) {
		this.plugin = plugin;
		this.interval = interval;
		this.repeatingTask = repeatingTask;
		this.stopped = true;
		this.async = false;
	}

	@Override
	public void run() {
		if (!stopped) runTask();
		else cancel();
	}

	public boolean isRunning() {
		return !stopped;
	}

	public void setInterval(long interval) {
		this.interval = interval;

		if (bukkitTask == null || stopped) return;

		stop();
		if (async) startAsync();
		else start();
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
		this.async = false;
	}

	public void startAsync() {
		if (bukkitTask != null && !stopped) return;
		this.bukkitTask = runTaskTimerAsynchronously(plugin, 0L, interval);
		stopped = false;
		this.async = true;
	}

	public void runTask() {
		repeatingTask.accept(this);
	}

}
