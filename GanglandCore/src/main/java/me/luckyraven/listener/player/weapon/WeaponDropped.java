package me.luckyraven.listener.player.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.ammo.Ammunition;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;

public class WeaponDropped implements Listener {

	private final Gangland gangland;

	public WeaponDropped(Gangland gangland) {
		this.gangland = gangland;
	}

	@EventHandler
	public void onPlayerDrop(PlayerDropItemEvent event) {
		ItemStack item = event.getItemDrop().getItemStack();
		// check if it was a weapon only
		if (!Weapon.isWeapon(item)) return;

		Player player = event.getPlayer();
		// drop the weapon normally
		if (!player.isSneaking()) return;

		// check if the weapon used is valid
		String weaponName = Weapon.getHeldWeaponName(item);
		if (weaponName == null) return;

		Initializer initializer = gangland.getInitializer();
		UUID        uuid        = Weapon.getWeaponUUID(item);
		Weapon      weapon      = initializer.getWeaponManager().getWeapon(uuid, weaponName);
		// check if the weapon is available
		if (weapon == null) return;

		// check if the player has the item
		boolean         found          = false;
		int             inventoryIndex = -1;
		PlayerInventory inventory      = player.getInventory();
		String          ammunitionName = weapon.getReloadAmmoType().getName();
		for (int i = 0; i < inventory.getSize(); i++) {
			ItemStack itemStack = inventory.getItem(i);

			if (itemStack == null) continue;
			if (!Ammunition.isAmmunition(itemStack)) continue;

			// get the ammunition type
			ItemBuilder itemBuilder = new ItemBuilder(itemStack);
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
