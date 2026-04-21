package me.luckyraven.weapon.listener.selective;

import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.bean.autowire.AutowireTarget;
import me.luckyraven.core.listener.ListenerHandler;
import me.luckyraven.core.utilities.ActionBarManager;
import me.luckyraven.core.utilities.ChatUtil;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.events.selective.WeaponChangeSelectiveFireEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

@ListenerHandler
@AutowireTarget({WeaponService.class})
public class WeaponSelectiveFireChangeListener implements Listener {

	private final WeaponService weaponService;

	public WeaponSelectiveFireChangeListener(WeaponService weaponService) {
		this.weaponService = weaponService;
	}

	@EventHandler
	public void onSwapHand(PlayerSwapHandItemsEvent event) {
		Player player = event.getPlayer();

		// check if the player is shifting
		if (!player.isSneaking()) return;

		// check if the player is holding a weapon with selective fire configured
		ItemStack item   = player.getInventory().getItemInMainHand();
		Weapon    weapon = weaponService.validateAndGetWeapon(player, item);

		if (weapon == null) return;
		if (weapon.getCurrentSelectiveFire() == null) return;

		var newEvent = new WeaponChangeSelectiveFireEvent(weapon);
		Bukkit.getPluginManager().callEvent(newEvent);

		if (newEvent.isCancelled()) return;

		// change the selective fire of the weapon and cancel opening the inventory
		event.setCancelled(true);

		weapon.setCurrentSelectiveFire(
				weapon.getCurrentSelectiveFire().getNextState(weapon.getAllowedSelectiveFires()));

		// update the weapon data
		ItemBuilder itemBuilder = weaponService.getHeldWeaponItem(player);

		if (itemBuilder == null) return;

		weapon.updateWeaponData(itemBuilder);
		weapon.updateWeapon(player, itemBuilder, player.getInventory().getHeldItemSlot());

		ActionBarManager.send(player, "&6Selective Fire > &e" +
		                              ChatUtil.capitalize(weapon.getCurrentSelectiveFire().name().toLowerCase()));
	}

}
