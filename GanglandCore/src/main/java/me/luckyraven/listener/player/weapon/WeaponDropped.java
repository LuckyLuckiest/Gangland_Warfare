package me.luckyraven.listener.player.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.WeaponManager;
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
		PlayerInventory inventory      = player.getInventory();
		String          ammunitionName = weapon.getReloadAmmoType().getName();

		if (inventory.containsAtLeast(weapon.getReloadAmmoType().buildItem(), weapon.getReloadConsume())) {

		}

		// don't drop the weapon if the player has ammunition item for it
		event.setCancelled(true);

		// remove the item accordingly
		weapon.reload(player);
	}

}
