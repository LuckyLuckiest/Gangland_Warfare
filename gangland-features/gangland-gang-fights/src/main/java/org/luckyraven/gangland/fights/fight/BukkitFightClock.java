package org.luckyraven.gangland.fights.fight;

import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;

/** {@link FightClock} on the server's sync scheduler. */
public final class BukkitFightClock implements FightClock {

	private final JavaPlugin plugin;

	public BukkitFightClock(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public Instant now() {
		return Instant.now();
	}

	@Override
	public void schedule(Runnable task, Duration delay) {
		plugin.getServer().getScheduler().runTaskLater(plugin, task, Math.max(1L, delay.toMillis() / 50L));
	}
}
