package org.luckyraven.gangland.copsncrooks.listener.turf;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.luckyraven.bartizan.api.event.WeaponRaytraceImpactEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.turf.npc.guard.TurfCopGuard;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.NpcSupport;

import java.util.UUID;

/**
 * Hands a player that a cop NPC damaged to the turf defenders: the cop hit is forwarded to {@link TurfCopGuard}, which
 * decides whether the victim is protected (standing on a turf owned by their gang, or an allied gang when enabled).
 * Covers the melee/projectile path and the Bartizan raytrace path so a gun shot counts the same as a punch.
 *
 * <p>Kept in cops-n-crooks because it is the only place that can recognise a cop ({@link CopManager#isCopNpc}); the turf
 * module never imports cops-n-crooks types.
 */
@ListenerHandler
public final class TurfCopProtectionListener implements Listener {

	private final CopManager   copManager;
	private final TurfCopGuard guard;

	public TurfCopProtectionListener(CopManager copManager, TurfCopGuard guard) {
		this.copManager = copManager;
		this.guard      = guard;
		// The guard releases a cop once its chase moves to an unprotected player (see TurfCopGuard#tick).
		guard.bindCopTargets(this::chasedPlayer);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDamage(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof Player victim)) return;
		if (NpcSupport.isNpc(victim)) return;

		LivingEntity cop = resolveAttacker(event.getDamager());
		if (cop == null || !copManager.isCopNpc(cop)) return;

		guard.onCopHitPlayer(cop, victim);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onWeaponImpact(WeaponRaytraceImpactEvent event) {
		if (!(event.getHitEntity() instanceof Player victim)) return;
		if (NpcSupport.isNpc(victim)) return;

		Entity shooter = event.getShooter();
		if (!(shooter instanceof LivingEntity cop) || !copManager.isCopNpc(cop)) return;

		guard.onCopHitPlayer(cop, victim);
	}

	/**
	 * The player {@code cop} is chasing, or {@code null} when it has none or that player is offline.
	 */
	private Player chasedPlayer(LivingEntity cop) {
		CopNpc npc = copManager.findCopByEntity(cop);
		UUID   id  = npc == null ? null : npc.getTargetPlayerId();
		return id == null ? null : Bukkit.getPlayer(id);
	}

	private static LivingEntity resolveAttacker(Entity damager) {
		if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
			return shooter;
		}
		return damager instanceof LivingEntity living ? living : null;
	}
}
