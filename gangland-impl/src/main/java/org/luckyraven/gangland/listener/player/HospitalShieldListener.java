package org.luckyraven.gangland.listener.player;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.data.teleportation.HospitalShield;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Enforces the hospital respawn shield: while a player holds it every incoming hit is cancelled (void excepted), and it
 * ends the moment he hits anything himself or quits. Runs at LOWEST so the cancelled hit never reaches the crime
 * ({@code EntityDamageListener}) or the downed ({@code CustomPlayerDeathListener}) handlers.
 */
@ListenerHandler
@RequiredArgsConstructor
public class HospitalShieldListener implements Listener {

	private final HospitalShield shield;

	@EventHandler(priority = EventPriority.LOWEST)
	public void onDamage(EntityDamageEvent event) {
		// attacking anything ends the shield, even an attack another plugin then cancels
		if (event instanceof EntityDamageByEntityEvent byEntity) {
			Player attacker = attackerOf(byEntity.getDamager());
			if (attacker != null) shield.end(attacker.getUniqueId());
		}

		if (!(event.getEntity() instanceof Player victim)) return;
		if (event.getCause() == EntityDamageEvent.DamageCause.VOID) return;
		if (shield.isShielded(victim.getUniqueId())) event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		shield.end(event.getPlayer().getUniqueId());
	}

	private static Player attackerOf(Entity damager) {
		if (damager instanceof Player player) return player;
		if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;

		return null;
	}

}
