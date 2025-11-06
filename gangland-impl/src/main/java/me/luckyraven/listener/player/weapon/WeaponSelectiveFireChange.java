package me.luckyraven.listener.player.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.feature.weapon.WeaponManager;
import me.luckyraven.listener.ListenerHandler;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.weapon.Weapon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

@ListenerHandler
public class WeaponSelectiveFireChange implements Listener {

	private final WeaponManager weaponManager;

	public WeaponSelectiveFireChange(Gangland gangland) {
		this.weaponManager = gangland.getInitializer().getWeaponManager();
	}

	@EventHandler
	public void onSwapHand(PlayerSwapHandItemsEvent event) {
		Player player = event.getPlayer();

		// check if the player is shifting
		if (!player.isSneaking()) return;

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

		ChatUtil.sendActionBar(player,
							   "&6Selective Fire > &e" +
							   ChatUtil.capitalize(weapon.getCurrentSelectiveFire().name().toLowerCase()));
	}

}
