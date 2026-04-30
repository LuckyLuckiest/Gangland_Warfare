package org.luckyraven.gangland.copsncrooks.listener.detainment;

import lombok.RequiredArgsConstructor;
import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.core.downed.PlayerDownedEvent;
import org.luckyraven.gangland.gang.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.gang.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.gang.events.wanted.WantedStartEvent;
import org.luckyraven.gangland.weapon.events.projectile.WeaponRaytraceImpactEvent;
import org.luckyraven.gangland.weapon.raytrace.WeaponRaytracer;

@ListenerHandler
@RequiredArgsConstructor
public class CopListener implements Listener {

	private final CopManager copManager;

	/**
	 * Starts cop pursuit when a player becomes wanted.
	 *
	 * @param event the wanted start event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onWantedStart(WantedStartEvent event) {
		copManager.onWantedStart(event.getPlayer(), event.getWanted());
	}

	/**
	 * Stops cop pursuit when a player is no longer wanted.
	 *
	 * @param event the wanted end event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onWantedEnd(WantedEndEvent event) {
		copManager.onWantedEnd(event.getPlayer());
	}

	/**
	 * Updates cop assignments when wanted level changes.
	 *
	 * @param event the wanted level change event
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onWantedChange(WantedLevelChangeEvent event) {
		copManager.onWantedLevelChange(event.getPlayer(), event.getWanted(), event.getOldLevel(), event.getNewLevel());
	}

	/**
	 * Cleans up cops when a player leaves the server.
	 *
	 * @param event the player quit event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		copManager.removeCopAttacker(player.getUniqueId());
		copManager.onWantedEnd(player);
	}

	/**
	 * Removes a player from the cop-attacker registry when they die so that cops do not continue attacking them after
	 * respawn.
	 *
	 * @param event the player death event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerDeath(PlayerDeathEvent event) {
		copManager.removeCopAttacker(event.getEntity().getUniqueId());
	}

	/**
	 * Removes a player from the cop-attacker registry when they are downed (GTA-style death that prevents actual
	 * death). Mirrors the behavior of {@link #onPlayerDeath} for the downed state.
	 *
	 * @param event the player downed event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerDowned(PlayerDownedEvent event) {
		copManager.removeCopAttacker(event.getPlayer().getUniqueId());
	}

	/**
	 * Handles a player attacking a cop NPC - forces the cop into combat mode.
	 *
	 * @param event the damage event
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onCopDamaged(EntityDamageByEntityEvent event) {
		// Weapon-system shots route through WeaponRaytraceImpactEvent (see onWeaponRaytraceImpact below).
		// Skip here to avoid double-processing the same shot via two separate code paths.
		if (WeaponRaytracer.isRaytraceDamageInProgress()) return;

		Entity victim = event.getEntity();

		if (!copManager.isCopNpc(victim)) return;

		Entity damager = event.getDamager();

		Player attacker;
		if (damager instanceof Player player && !CitizensAPI.getNPCRegistry().isNPC(player)) {
			attacker = player;
		} else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player
		           && !CitizensAPI.getNPCRegistry().isNPC(player)) {
			attacker = player;
		} else {
			// Friendly fire: cancel damage when a cop is hit by another cop's attack
			boolean isCopFire = copManager.isCopNpc(damager) ||
			                    (damager instanceof Projectile p && p.getShooter() instanceof Entity shooterEntity &&
			                     copManager.isCopNpc(shooterEntity));
			if (isCopFire) {
				event.setCancelled(true);
				return;
			}

			// Non-cop NPC attacker — queue it so this cop engages after finishing with the player
			CopNpc attackedCop = copManager.findCopByEntity(victim);
			if (attackedCop != null) {
				LivingEntity entityAttacker = null;
				if (damager instanceof LivingEntity le) {
					entityAttacker = le;
				} else if (damager instanceof Projectile proj && proj.getShooter() instanceof LivingEntity le) {
					entityAttacker = le;
				}
				if (entityAttacker != null) {
					attackedCop.addEntityAttacker(entityAttacker);
				}
			}
			return;
		}

		CopNpc cop = copManager.findCopByEntity(victim);
		if (cop == null) return;

		// Alert system: put ALL cops for this player into combat mode
		copManager.onCopAttackedAlert(cop, attacker);
	}

	/**
	 * Handles weapon-system shots against cop NPCs. Canonical hook for any gangland weapon hitting a cop. Mirrors the
	 * legacy {@link #onCopDamaged} branches but operates on {@link WeaponRaytraceImpactEvent}, which is fired by
	 * {@code WeaponRaytracer} for every unified hit detection result. Critical: this handler also enforces the cop
	 * friendly-fire cancel — if both shooter and victim are cop NPCs, {@code event.setCancelled(true)} stops the
	 * raytracer from applying damage.
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onWeaponRaytraceImpact(WeaponRaytraceImpactEvent event) {
		Entity victim = event.getHitEntity();
		if (victim == null) return;

		if (!copManager.isCopNpc(victim)) return;

		LivingEntity shooter = event.getShooter();
		if (shooter == null) return;

		// Friendly fire: cancel when a cop is hit by another cop's weapon
		if (copManager.isCopNpc(shooter)) {
			event.setCancelled(true);
			return;
		}

		if (shooter instanceof Player attacker && !CitizensAPI.getNPCRegistry().isNPC(attacker)) {
			CopNpc cop = copManager.findCopByEntity(victim);
			if (cop == null) return;
			copManager.onCopAttackedAlert(cop, attacker);
			return;
		}

		// Non-player, non-cop LivingEntity attacker (hostile civilian, etc.)
		CopNpc attackedCop = copManager.findCopByEntity(victim);
		if (attackedCop != null) {
			attackedCop.addEntityAttacker(shooter);
		}
	}

	/**
	 * Clears drops when a cop NPC is killed.
	 *
	 * @param event the death event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onCopDeath(EntityDeathEvent event) {
		if (!copManager.isCopNpc(event.getEntity())) return;

		CopNpc cop = copManager.findCopByEntity(event.getEntity());
		if (cop != null) {
			cop.destroy();
		}

		event.getDrops().clear();
		event.setDroppedExp(0);

		if (event instanceof PlayerDeathEvent playerDeathEvent) {
			playerDeathEvent.setKeepInventory(true);
		}
	}
}