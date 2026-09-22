package org.luckyraven.gangland.civilians.listener.gang;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.bartizan.api.event.WeaponRaytraceImpactEvent;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.file.configuration.Settings;
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
 *
 * <p>Rewritten onto the always-present {@link GangMembership} holder (WS5 G2 S1) instead of {@code GangManager}/
 * {@code UserManager} — civilians stays gang-module-free, no {@code Depends: [gang]} edge. {@code alliedOrSame}
 * already excludes the "both gang-less" false positive (its {@code idA != -1} guard), so the old explicit
 * {@code hasGang()} check on both sides is redundant, not dropped behaviour.
 */
@ListenerHandler(condition = "isBartizanAvailable")
public class GangAllyWeaponImpactListener implements Listener {

	private final GangMembership membership;

	public GangAllyWeaponImpactListener(GangMembership membership) {
		this.membership = membership;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onGangMemberWeaponImpact(WeaponRaytraceImpactEvent event) {
		if (!Settings.isGangEnabled()) return;
		if (!(event.getShooter() instanceof Player damager)) return;
		if (!(event.getHitEntity() instanceof Player damaged)) return;

		if (membership.alliedOrSame(damager.getUniqueId(), damaged.getUniqueId())) {
			event.setCancelled(true);
		}
	}

}
