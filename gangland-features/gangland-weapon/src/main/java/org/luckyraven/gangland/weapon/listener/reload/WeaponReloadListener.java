package org.luckyraven.gangland.weapon.listener.reload;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponService;
import org.luckyraven.gangland.weapon.events.reload.WeaponReloadCompleteEvent;
import org.luckyraven.gangland.weapon.events.reload.WeaponReloadStartEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@ListenerHandler
@AutowireTarget({WeaponService.class})
@RequiredArgsConstructor
public class WeaponReloadListener implements Listener {

	private final WeaponService weaponService;
	private final Set<UUID>     reloadingPlayers = new HashSet<>();

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

		ItemStack heldItem = player.getInventory().getItemInMainHand();
		Weapon    weapon   = weaponService.validateAndGetWeapon(player, heldItem);

		if (weapon == null) return;

		weapon.stopReloading();
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		reloadingPlayers.remove(event.getPlayer().getUniqueId());
	}

}
