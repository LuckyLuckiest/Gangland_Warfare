package me.luckyraven.weapon.listener.reload;

import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.events.reload.WeaponReloadCompleteEvent;
import me.luckyraven.weapon.events.reload.WeaponReloadStartEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@ListenerHandler
public class WeaponReloadListener implements Listener {

	private final Set<UUID> reloadingPlayers = new HashSet<>();

	@EventHandler
	public void onReloadStart(WeaponReloadStartEvent event) {
		reloadingPlayers.add(event.getPlayer().getUniqueId());
	}

	@EventHandler
	public void onReloadEnd(WeaponReloadCompleteEvent event) {
		reloadingPlayers.remove(event.getPlayer().getUniqueId());
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onHeldSlotChange(PlayerItemHeldEvent event) {
		Player player = event.getPlayer();

		if (!reloadingPlayers.contains(player.getUniqueId())) return;

		event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerDrop(PlayerDropItemEvent event) {
		Player player = event.getPlayer();

		if (!reloadingPlayers.contains(player.getUniqueId())) return;

		ItemStack dropped = event.getItemDrop().getItemStack();

		// only ammo items may be dropped during a reload
		if (!Ammunition.isAmmunition(dropped)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerPickup(EntityPickupItemEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;

		if (!reloadingPlayers.contains(player.getUniqueId())) return;

		ItemStack item = event.getItem().getItemStack();

		// only ammo items may be picked up during a reload
		if (!Ammunition.isAmmunition(item)) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		reloadingPlayers.remove(event.getPlayer().getUniqueId());
	}

}
