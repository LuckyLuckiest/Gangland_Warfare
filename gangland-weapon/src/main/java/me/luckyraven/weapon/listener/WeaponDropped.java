package me.luckyraven.weapon.listener;

import me.luckyraven.util.autowire.AutowireTarget;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.utilities.ChatUtil;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
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
public class WeaponDropped implements Listener {

	private final JavaPlugin    plugin;
	private final WeaponService weaponService;

	public WeaponDropped(JavaPlugin plugin, WeaponService weaponService) {
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

		// drop the weapon normally
		if (!player.isSneaking()) return;

		// check for full ammo
		boolean requiresReload = weapon.requiresReload(!player.getGameMode().equals(GameMode.CREATIVE));

		if (!requiresReload) return;

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
