package org.luckyraven.gangland.weapon.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponService;
import org.luckyraven.gangland.weapon.dto.ScopeData;

/**
 * Prevents the player from jumping while a scoped weapon is active.
 *
 * <p>Uses {@link PlayerMoveEvent} (Spigot-compatible) to detect upward position
 * changes and snaps Y back so the jump is canceled. Covers both manual scope (left-click) and the scope applied
 * automatically at the start of a reload.
 */
@ListenerHandler
@AutowireTarget({WeaponService.class})
public class ScopeJumpListener implements Listener {

	private final WeaponService weaponService;

	public ScopeJumpListener(WeaponService weaponService) {
		this.weaponService = weaponService;
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerMove(PlayerMoveEvent event) {
		Location to   = event.getTo();
		Location from = event.getFrom();
		// Only act when the player is moving upward
		if (to == null || to.getY() <= from.getY()) return;

		Player    player = event.getPlayer();
		ItemStack item   = player.getInventory().getItemInMainHand();
		Weapon    weapon = weaponService.validateAndGetWeapon(player, item);
		if (weapon == null) return;

		// During reload the scope is auto-applied as an animation effect; blocking Y movement would
		// prevent external knockback from working properly while the player is being attacked.
		if (weapon.isReloading()) return;

		ScopeData scopeData = weapon.getScopeData();
		if (scopeData == null || !scopeData.isScoped()) return;

		// Snap the destination Y back to the source Y to block the jump
		Location blocked = to.clone();
		blocked.setY(from.getY());
		event.setTo(blocked);
	}

}