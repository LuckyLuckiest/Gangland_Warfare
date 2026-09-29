package org.luckyraven.gangland.healthbars.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.healthbars.bar.NpcHealthBar;
import org.luckyraven.gangland.healthbars.config.HealthBarSettings;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Redraws an NPC's health bar after it takes damage or heals. The health is not applied yet at {@code MONITOR}, so the
 * bar is drawn on the next tick. Only plain Bukkit types appear here; the Citizens work is in {@link NpcHealthBar}.
 */
@ListenerHandler
@RequiredArgsConstructor
@AutowireTarget({JavaPlugin.class, HealthBarSettings.class})
public class HealthBarListener implements Listener {

	private final JavaPlugin        plugin;
	private final HealthBarSettings settings;

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		redrawNextTick(event.getEntity());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onRegainHealth(EntityRegainHealthEvent event) {
		redrawNextTick(event.getEntity());
	}

	private void redrawNextTick(Entity entity) {
		// Citizens tags every NPC entity with "NPC" metadata: skips the scheduler for players and ordinary mobs.
		if (!entity.hasMetadata("NPC")) return;
		plugin.getServer().getScheduler().runTask(plugin, () -> NpcHealthBar.update(entity, settings));
	}
}
