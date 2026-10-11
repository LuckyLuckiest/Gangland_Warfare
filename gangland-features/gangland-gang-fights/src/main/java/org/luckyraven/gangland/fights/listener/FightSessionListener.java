package org.luckyraven.gangland.fights.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.fights.FightRules;
import org.luckyraven.gangland.fights.arena.ArenaRegistry;
import org.luckyraven.gangland.fights.fight.FightManager;
import org.luckyraven.gangland.fights.fight.FightMessages;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

@ListenerHandler
public class FightSessionListener implements Listener {

	public FightSessionListener(org.bukkit.plugin.java.JavaPlugin plugin, FightManager manager, ArenaRegistry arenas, FightMessages messages) {
	}

	@EventHandler
	public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
	}

	@EventHandler
	public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
	}

	@EventHandler
	public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
	}

	@EventHandler
	public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
	}
}
