package org.luckyraven.gangland.weapon.listener.reload;

import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.core.bean.autowire.AutowireTarget;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.core.utilities.ChatUtil;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponService;

/**
 * Attaches the floating weapon name to any {@link Item} entity whose stack resolves to a weapon configured with
 * {@code Drop_Hologram}. {@link WeaponDroppedListener} covers only {@code PlayerDropItemEvent} (Q / drag-out), so death
 * drops, Death.Respawn drops, and any other {@code dropItemNaturally} path previously spawned hologram-less.
 */
@ListenerHandler
@AutowireTarget({WeaponService.class})
public class WeaponItemSpawnListener implements Listener {

	private final WeaponService weaponService;

	public WeaponItemSpawnListener(WeaponService weaponService) {
		this.weaponService = weaponService;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onItemSpawn(ItemSpawnEvent event) {
		Item      item   = event.getEntity();
		ItemStack stack  = item.getItemStack();
		Weapon    weapon = weaponService.validateAndGetWeapon(null, stack);

		if (weapon == null || !weapon.isDropHologram()) return;

		item.setCustomName(ChatUtil.color(weapon.getDisplayName()));
		item.setCustomNameVisible(true);
	}

}
