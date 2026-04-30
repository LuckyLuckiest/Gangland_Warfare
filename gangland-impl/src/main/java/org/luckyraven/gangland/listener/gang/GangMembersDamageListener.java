package org.luckyraven.gangland.listener.gang;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.luckyraven.gangland.core.bean.Qualifier;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.weapon.events.projectile.WeaponRaytraceImpactEvent;

@ListenerHandler(condition = "isGangEnabled")
public class GangMembersDamageListener implements Listener {

	private final UserManager<Player> userManager;
	private final GangManager         gangManager;

	public GangMembersDamageListener(@Qualifier("online") UserManager<Player> userManager, GangManager gangManager) {
		this.userManager = userManager;
		this.gangManager = gangManager;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onGangMemberHitMembers(EntityDamageByEntityEvent event) {
		// checks for player-to-player damage
		Player damager;
		if (event.getDamager() instanceof Player player) damager = player;
		else if (event.getDamager() instanceof Projectile projectile) {
			if (projectile.getShooter() instanceof Player player) damager = player;
			else return;
		} else return;
		if (!(event.getEntity() instanceof Player damaged)) return;

		User<Player> userDamager = userManager.getUser(damager);
		User<Player> userDamaged = userManager.getUser(damaged);

		// checks if they are in a gang
		if (userDamager == null || userDamaged == null || !(userDamager.hasGang() && userDamaged.hasGang())) return;

		// checks if they are alias or in the same gang
		Gang gang1 = gangManager.getGang(userDamager.getGangId());
		Gang gang2 = gangManager.getGang(userDamaged.getGangId());

		if (gang1.isAlly(gang2) || userDamager.getGangId() == userDamaged.getGangId()) event.setCancelled(true);
	}

	// Mirrors onGangMemberHitMembers on the canonical weapon-impact event so weapons whose damage path bypasses
	// EntityDamageByEntityEvent (flamethrower fire ticks, biological clouds, melee custom handlers, etc.) are still
	// cancelled when both shooter and target are gang members or allies.
	@EventHandler(priority = EventPriority.LOWEST)
	public void onGangMemberWeaponImpact(WeaponRaytraceImpactEvent event) {
		if (!(event.getShooter() instanceof Player damager)) return;
		if (!(event.getHitEntity() instanceof Player damaged)) return;

		User<Player> userDamager = userManager.getUser(damager);
		User<Player> userDamaged = userManager.getUser(damaged);

		if (userDamager == null || userDamaged == null || !(userDamager.hasGang() && userDamaged.hasGang())) return;

		Gang gang1 = gangManager.getGang(userDamager.getGangId());
		Gang gang2 = gangManager.getGang(userDamaged.getGangId());

		if (gang1.isAlly(gang2) || userDamager.getGangId() == userDamaged.getGangId()) event.setCancelled(true);
	}

}
