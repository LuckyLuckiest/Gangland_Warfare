package me.luckyraven.listener.player.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.WeaponManager;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;

public class WeaponSelectiveFireChange implements Listener {

	private final Gangland      gangland;
	private final WeaponManager weaponManager;

	public WeaponSelectiveFireChange(Gangland gangland) {
		this.gangland      = gangland;
		this.weaponManager = gangland.getInitializer().getWeaponManager();
	}

	@EventHandler
	public void onInventoryOpen(InventoryOpenEvent event) {
		HumanEntity humanEntity = event.getPlayer();

		// validate that it is the player
		if (!(humanEntity instanceof Player player)) return;

		// check if the player is trying to open their inventory
		if (!event.getInventory().equals(player.getInventory())) return;

		// check if the player is holding a weapon
		ItemStack item   = player.getInventory().getItemInMainHand();
		Weapon    weapon = weaponManager.validateAndGetWeapon(player, item);

		if (weapon == null) return;

		// change the selective fire of the weapon and cancel opening the inventory
		event.setCancelled(true);

		weapon.setCurrentSelectiveFire(weapon.getCurrentSelectiveFire().getNextState());

		// update the weapon data
		ItemBuilder itemBuilder = weaponManager.getHeldWeaponItem(player);

		if (itemBuilder == null) return;

		weapon.updateWeaponData(itemBuilder);
		weapon.updateWeapon(player, itemBuilder, player.getInventory().getHeldItemSlot());
	}

}
