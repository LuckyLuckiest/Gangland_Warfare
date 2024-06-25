package me.luckyraven.listener.player.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.WeaponManager;
import me.luckyraven.feature.weapon.ammo.Ammunition;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class WeaponDropped implements Listener {

	private final WeaponManager weaponManager;

	public WeaponDropped(Gangland gangland) {
		this.weaponManager = gangland.getInitializer().getWeaponManager();
	}

	@EventHandler
	public void onPlayerDrop(PlayerDropItemEvent event) {
		Player    player    = event.getPlayer();
		Item      item      = event.getItemDrop();
		ItemStack itemStack = item.getItemStack();
		Weapon    weapon    = weaponManager.validateAndGetWeapon(player, itemStack);

		if (weapon == null) return;

		// show the hologram when the weapon is dropped
		if (weapon.isDropHologram()) {
			item.setCustomName(weapon.getDisplayName());
			item.setCustomNameVisible(true);
		}

		// drop the weapon normally
		if (!player.isSneaking()) return;

		// check if the player has the item
		boolean         found          = false;
		int             inventoryIndex = -1;
		PlayerInventory inventory      = player.getInventory();
		String          ammunitionName = weapon.getReloadAmmoType().getName();
		for (int i = 0; i < inventory.getSize(); i++) {
			ItemStack inventoryItem = inventory.getItem(i);

			if (inventoryItem == null || inventoryItem.getType().equals(Material.AIR) || inventoryItem.getAmount() == 0)
				continue;
			if (!Ammunition.isAmmunition(inventoryItem)) continue;

			// get the ammunition type
			ItemBuilder itemBuilder = new ItemBuilder(inventoryItem);
			// get the ammunition name
			String name = itemBuilder.getStringTagData("ammo");

			if (!ammunitionName.equalsIgnoreCase(name)) continue;

			inventoryIndex = i;
			found          = true;
		}

		if (!found) return;

		// don't drop the weapon if the player has ammunition item for it
		event.setCancelled(true);

		// remove the item accordingly
		weapon.reload();
	}

}
