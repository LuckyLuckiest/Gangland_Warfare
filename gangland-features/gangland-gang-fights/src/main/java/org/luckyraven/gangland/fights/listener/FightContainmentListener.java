package org.luckyraven.gangland.fights.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.fights.FightRules;
import org.luckyraven.gangland.fights.arena.ArenaRegistry;
import org.luckyraven.gangland.fights.fight.FightManager;
import org.luckyraven.gangland.fights.fight.FightMessages;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

@ListenerHandler
public class FightContainmentListener implements Listener {

	public FightContainmentListener(org.bukkit.plugin.java.JavaPlugin plugin, FightManager manager, ArenaRegistry arenas, FightRules rules, FightMessages messages) {
	}

	@EventHandler
	public void onMove(org.bukkit.event.player.PlayerMoveEvent event) {
	}

	@EventHandler
	public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
	}

	@EventHandler
	public void onPortal(org.bukkit.event.player.PlayerPortalEvent event) {
	}

	@EventHandler
	public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
	}

	@EventHandler
	public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent event) {
	}

	@EventHandler
	public void onVehicleEnter(org.bukkit.event.vehicle.VehicleEnterEvent event) {
	}

	public void check(java.util.Collection<? extends org.bukkit.entity.Player> online) {
	}
}
