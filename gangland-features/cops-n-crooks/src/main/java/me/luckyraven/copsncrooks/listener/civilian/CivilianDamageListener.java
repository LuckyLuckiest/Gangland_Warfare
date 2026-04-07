package me.luckyraven.copsncrooks.listener.civilian;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.civilian.CivilianState;
import me.luckyraven.copsncrooks.npc.civilian.config.CivilianAIBehaviorConfig;
import me.luckyraven.copsncrooks.npc.civilian.npc.CivilianNpc;
import me.luckyraven.util.downed.PlayerDownedEvent;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.weapon.events.projectile.WeaponRaytraceImpactEvent;
import me.luckyraven.weapon.raytrace.WeaponRaytracer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.UUID;

/**
 * Reacts to damage dealt to civilian NPCs:
 * <ul>
 *   <li>Non-hostile civilians flee toward safety.</li>
 *   <li>Hostile civilians enter combat against their attacker.</li>
 * </ul>
 * Only player attackers trigger AI state changes.
 */
@ListenerHandler
@RequiredArgsConstructor
public class CivilianDamageListener implements Listener {

	private final CivilianService civilianService;

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCivilianDamage(EntityDamageByEntityEvent event) {
		// Weapon-system shots route through WeaponRaytraceImpactEvent (see onWeaponRaytraceImpact below).
		// Skip here to avoid double-processing the same shot via two separate code paths.
		if (WeaponRaytracer.isRaytraceDamageInProgress()) return;

		Entity damaged = event.getEntity();

		CivilianNpc npc = civilianService.getNpc(damaged.getUniqueId());
		if (npc == null) return;

		Entity                   damager = event.getDamager();
		CivilianAIBehaviorConfig ai      = npc.getTypeConfig().ai();

		// Player attacker (direct hit or via projectile)
		Player playerAttacker = null;
		if (damager instanceof Player p) {
			playerAttacker = p;
		} else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
			playerAttacker = p;
		}

		if (playerAttacker != null) {
			if (npc.isHostile() && ai.combatEnabled()) {
				npc.setTargetPlayerId(playerAttacker.getUniqueId());
				if (npc.getCurrentState() != CivilianState.COMBAT) {
					npc.transitionTo(CivilianState.COMBAT);
				}
			} else if (!npc.isHostile() && ai.fleeEnabled()) {
				if (npc.getCurrentState() != CivilianState.FLEEING) {
					npc.setLastAttackerLocation(playerAttacker.getLocation().clone());
					npc.transitionTo(CivilianState.FLEEING);
				}
			}
			return;
		}

		// Non-player LivingEntity attacker (NPC-to-NPC: cop shoots civilian, civilian shoots civilian, etc.)
		LivingEntity entityAttacker = null;
		if (damager instanceof LivingEntity le) {
			entityAttacker = le;
		} else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity le) {
			entityAttacker = le;
		}

		if (entityAttacker != null) {
			if (npc.isHostile() && ai.combatEnabled()) {
				// Bump the attacker to the front of the entity target queue so the civilian
				// immediately fights back, regardless of what it was previously targeting.
				npc.addEntityTargetToFront(entityAttacker);
				if (npc.getCurrentState() != CivilianState.COMBAT) {
					npc.transitionTo(CivilianState.COMBAT);
				}
			} else if (!npc.isHostile() && ai.fleeEnabled()) {
				if (npc.getCurrentState() != CivilianState.FLEEING) {
					npc.setLastAttackerLocation(entityAttacker.getLocation().clone());
					npc.transitionTo(CivilianState.FLEEING);
				}
			}
		}
	}

	/**
	 * Handles weapon-system shots against civilian NPCs. This is the canonical hook for any gangland weapon (gun,
	 * incendiary, biological, melee, throwable) hitting a civilian — the legacy {@link #onCivilianDamage} handler skips
	 * during a raytrace via the {@link WeaponRaytracer#isRaytraceDamageInProgress()} guard.
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onWeaponRaytraceImpact(WeaponRaytraceImpactEvent event) {
		Entity damaged = event.getHitEntity();
		if (damaged == null) return;

		CivilianNpc npc = civilianService.getNpc(damaged.getUniqueId());
		if (npc == null) return;

		LivingEntity attacker = event.getShooter();
		if (attacker == null) return;

		CivilianAIBehaviorConfig ai = npc.getTypeConfig().ai();

		if (attacker instanceof Player playerAttacker) {
			if (npc.isHostile() && ai.combatEnabled()) {
				npc.setTargetPlayerId(playerAttacker.getUniqueId());
				if (npc.getCurrentState() != CivilianState.COMBAT) {
					npc.transitionTo(CivilianState.COMBAT);
				}
			} else if (!npc.isHostile() && ai.fleeEnabled()) {
				if (npc.getCurrentState() != CivilianState.FLEEING) {
					npc.setLastAttackerLocation(playerAttacker.getLocation().clone());
					npc.transitionTo(CivilianState.FLEEING);
				}
			}
			return;
		}

		// Non-player LivingEntity shooter (cop NPC, hostile civilian, etc.)
		if (npc.isHostile() && ai.combatEnabled()) {
			npc.addEntityTargetToFront(attacker);
			if (npc.getCurrentState() != CivilianState.COMBAT) {
				npc.transitionTo(CivilianState.COMBAT);
			}
		} else if (!npc.isHostile() && ai.fleeEnabled()) {
			if (npc.getCurrentState() != CivilianState.FLEEING) {
				npc.setLastAttackerLocation(attacker.getLocation().clone());
				npc.transitionTo(CivilianState.FLEEING);
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerDowned(PlayerDownedEvent event) {
		clearCivilianTargets(event.getPlayer().getUniqueId());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerDeath(PlayerDeathEvent event) {
		clearCivilianTargets(event.getEntity().getUniqueId());
	}

	private void clearCivilianTargets(UUID playerId) {
		for (CivilianNpc npc : civilianService.getActiveNpcs()) {
			if (playerId.equals(npc.getTargetPlayerId())) {
				npc.transitionTo(CivilianState.IDLE);
			}
		}
	}
}
