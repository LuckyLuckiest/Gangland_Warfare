package org.luckyraven.gangland.copsncrooks.bag;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.luckyraven.keystone.bean.BeanLifecycle;

import java.util.function.Supplier;

/**
 * Repeating main-thread task that refreshes {@code Bag: $X} on the action bar every {@code Hud_Interval_Ticks}.
 *
 * @since 0.20.0
 */
public final class BagHud implements BeanLifecycle {

	private final JavaPlugin plugin;
	private final BagService service;
	private final Supplier<BagSettings> settings;
	private volatile BukkitTask task;

	public BagHud(JavaPlugin plugin, BagService service, Supplier<BagSettings> settings) {
		this.plugin   = plugin;
		this.service  = service;
		this.settings = settings;
	}

	/** Starts (or restarts) the repeating task at the configured interval. */
	public void start() {
		stop();
		BagSettings cfg = settings.get();
		if (!cfg.hudEnable()) {
			return;
		}
		long interval = Math.max(1, cfg.hudIntervalTicks());
		task = plugin.getServer().getScheduler().runTaskTimer(plugin,
		                                                      () -> service.refreshHud(plugin.getServer().getOnlinePlayers()),
		                                                      interval, interval);
	}

	public void stop() {
		if (task == null) {
			return;
		}
		task.cancel();
		task = null;
	}

	/** Reload pass: the pre-clear shutdown hook stopped the task, so restart it. */
	@Override
	public void onInitialize(boolean firstLoad) {
		start();
	}

	@Override
	public void onClear() {
		stop();
	}
}
