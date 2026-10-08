package org.luckyraven.gangland.mail;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.timer.RepeatingTimer;

/**
 * Once-a-second sweep that expires due mail. It runs on the MAIN thread (synchronous scheduler): the sweep deletes and
 * mutates {@link MailItem}s that commands touch on the main thread, so an async tick would race them (GR-11).
 */
public final class MailExpirySweep implements BeanLifecycle {

	private static final long EXPIRY_TICK_INTERVAL = 20L; // 1 second

	private final JavaPlugin  plugin;
	private final MailManager manager;

	private RepeatingTimer timer;

	public MailExpirySweep(JavaPlugin plugin, MailManager manager) {
		this.plugin  = plugin;
		this.manager = manager;
	}

	public synchronized void start() {
		if (timer != null) return;

		timer = new RepeatingTimer(plugin, EXPIRY_TICK_INTERVAL, t -> manager.expireDue());
		timer.start(false);
	}

	public synchronized void stop() {
		if (timer == null) return;

		timer.stop();
		timer = null;
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		start();
	}

	@Override
	public void onPreClear() {
		stop();
	}

	@Override
	public void onShutdown() {
		stop();
	}

}
