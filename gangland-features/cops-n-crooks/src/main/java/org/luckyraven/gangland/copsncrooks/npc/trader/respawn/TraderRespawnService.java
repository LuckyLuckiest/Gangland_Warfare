package org.luckyraven.gangland.copsncrooks.npc.trader.respawn;

import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.trader.TraderData;
import org.luckyraven.gangland.copsncrooks.npc.trader.config.TraderSettings;
import org.luckyraven.gangland.core.bean.BeanLifecycle;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@CustomLog
@RequiredArgsConstructor
public final class TraderRespawnService implements BeanLifecycle {

	private final JavaPlugin     plugin;
	private final TraderSettings settings;

	private final Set<UUID> pending = new HashSet<>();

	public void schedule(TraderData data, Consumer<TraderData> respawnCallback) {
		if (!pending.add(data.getId())) return;

		long delayTicks = (long) settings.getRespawnCooldownSeconds() * 20L;

		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			pending.remove(data.getId());
			try {
				respawnCallback.accept(data);
			} catch (Exception e) {
				log.warn("Failed to respawn trader {}: {}", data.getId(), e.getMessage());
			}
		}, delayTicks);
	}

	public boolean isPending(UUID traderId) {
		return pending.contains(traderId);
	}

	public void cancelAll() {
		pending.clear();
	}

	@Override
	public void onClear() {
		cancelAll();
	}

}
