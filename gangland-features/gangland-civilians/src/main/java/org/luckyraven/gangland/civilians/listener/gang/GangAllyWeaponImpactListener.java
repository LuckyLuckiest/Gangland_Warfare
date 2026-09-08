package org.luckyraven.gangland.civilians.listener.gang;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.bartizan.api.event.WeaponRaytraceImpactEvent;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Mirrors the core's {@code GangMembersDamageListener.onGangMemberHitMembers} on the canonical Bartizan weapon-impact
 * event so weapons whose damage path bypasses {@code EntityDamageByEntityEvent} (flamethrower fire ticks, biological
 * clouds, melee custom handlers, etc.) are still cancelled when both shooter and target are gang members or allies.
 *
 * <p>Moved from the deleted weapon module (T-H5); lives in {@code gangland-civilians} rather than the core because
 * the core carries zero {@code org.luckyraven.bartizan} references (PICK Amendments ruling (a)). The module already
 * declares {@code Plugins: [Bartizan]}, so the module loader skips this class entirely when Bartizan is absent — no
 * manual registration, no lazy-class-loading trick.
 */
@ListenerHandler(condition = "isGangEnabled")
public class GangAllyWeaponImpactListener implements Listener {

	private final UserManager<Player> userManager;
	private final GangManager         gangManager;

	public GangAllyWeaponImpactListener(@Qualifier("online") UserManager<Player> userManager,
	                                    GangManager gangManager) {
		this.userManager = userManager;
		this.gangManager = gangManager;
	}

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
