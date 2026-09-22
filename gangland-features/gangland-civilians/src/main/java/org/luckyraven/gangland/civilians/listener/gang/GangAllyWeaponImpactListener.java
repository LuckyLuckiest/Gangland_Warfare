package org.luckyraven.gangland.civilians.listener.gang;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.bartizan.api.event.WeaponRaytraceImpactEvent;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Mirrors the core's {@code GangMembersDamageListener.onGangMemberHitMembers} on the canonical Bartizan weapon-impact
 * event so weapons whose damage path bypasses {@code EntityDamageByEntityEvent} (flamethrower fire ticks, biological
 * clouds, melee custom handlers, etc.) are still cancelled when both shooter and target are gang members or allies.
 *
 * <p>Moved from the deleted weapon module (T-H5); lives in {@code gangland-civilians} rather than the core because
 * the core carries zero {@code org.luckyraven.bartizan} references (PICK Amendments ruling (a)). WS7 G5b: the
 * module dropped its hard {@code Plugins: [Bartizan]} dependency, so this class instead gates its own
 * construction/registration with {@code @ListenerHandler(condition = "isBartizanAvailable")} — same reasoning as
 * {@link Settings#isBartizanAvailable()}'s javadoc: Bukkit's reflective scan resolves this {@code @EventHandler}
 * method's {@link WeaponRaytraceImpactEvent} parameter type eagerly, and on a Bartizan-less server that throws
 * {@code NoClassDefFoundError} unless construction is skipped entirely. The gang-enabled check that used to be the
 * gate now runs as an ordinary early-return inside the handler body.
 */
@ListenerHandler(condition = "isBartizanAvailable")
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
		if (!Settings.isGangEnabled()) return;
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
