package org.luckyraven.gangland.weapon.listener.player;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponManager;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.bean.listener.ListenerPriority;

/**
 * The weapon half of the core's quit cleanup, moved out of {@code RemoveAccountListener} when the feature flipped
 * to a runtime module: unscopes and stops reloading whatever weapon the player was holding when they quit.
 */
@ListenerHandler(priority = ListenerPriority.LOW)
public class WeaponQuitCleanupListener implements Listener {

	private final WeaponManager weaponManager;

	public WeaponQuitCleanupListener(WeaponManager weaponManager) {
		this.weaponManager = weaponManager;
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();

		// search if the player holds a weapon
		// check if it was a weapon
		ItemStack item   = player.getInventory().getItemInMainHand();
		Weapon    weapon = weaponManager.validateAndGetWeapon(player, item);

		if (weapon == null) return;
		if (weapon.isReloading()) weapon.stopReloading();

		weapon.unScope(player, true);
	}

}
