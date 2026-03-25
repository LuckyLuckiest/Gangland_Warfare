package me.luckyraven.weapon.listener.reload;

import me.luckyraven.util.autowire.AutowireTarget;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.utilities.ChatUtil;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.events.reload.WeaponReloadEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

@ListenerHandler
@AutowireTarget({WeaponService.class})
public class WeaponDroppedListener implements Listener {

	private final JavaPlugin    plugin;
	private final WeaponService weaponService;

	public WeaponDroppedListener(JavaPlugin plugin, WeaponService weaponService) {
		this.plugin        = plugin;
		this.weaponService = weaponService;
	}

	@EventHandler
	public void onPlayerDrop(PlayerDropItemEvent event) {
		Player    player    = event.getPlayer();
		Item      item      = event.getItemDrop();
		ItemStack itemStack = item.getItemStack();
		Weapon    weapon    = weaponService.validateAndGetWeapon(player, itemStack);

		if (weapon == null) return;

		var newEvent = new WeaponReloadEvent(weapon);
		Bukkit.getPluginManager().callEvent(newEvent);

		if (newEvent.isCancelled()) return;

		// no interruption while the weapon is reloading
		if (weapon.isReloading()) {
			event.setCancelled(true);
			return;
		}

		// show the hologram when the weapon is dropped
		if (weapon.isDropHologram()) {
			item.setCustomName(ChatUtil.color(weapon.getDisplayName()));
			item.setCustomNameVisible(true);
		}

		// weapons without ammo (melee, throwable, etc.) skip reload
		if (weapon.getReloadData() == null) return;

		// drop the weapon normally
		if (!player.isSneaking()) return;

		// check if the magazine is already full
		if (weapon.isMagazineFull()) {
			ChatUtil.sendActionBar(player, "&cMagazine is full!");
			event.setCancelled(true);
			return;
		}

		// check if the item is available or it was creative
		boolean haveItem = weaponService.hasAmmunition(player, weapon);
		boolean creative = player.getGameMode() == GameMode.CREATIVE;

		if (!(haveItem || creative)) return;

		// don't drop the weapon if the player has ammunition item for it
		event.setCancelled(true);

		// reload the weapon
		weapon.reload(plugin, player, !creative);
	}

}
