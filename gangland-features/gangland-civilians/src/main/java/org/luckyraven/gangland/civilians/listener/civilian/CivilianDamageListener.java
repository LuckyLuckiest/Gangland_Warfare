package org.luckyraven.gangland.civilians.listener.civilian;

import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.config.CivilianAIBehaviorConfig;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.witness.WitnessService;
import org.luckyraven.gangland.npc.NpcRole;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.gangland.core.downed.PlayerDownedEvent;

import java.util.UUID;

/**
 * Reacts to damage dealt to civilian NPCs:
 * <ul>
 *   <li>Non-hostile civilians flee toward safety.</li>
 *   <li>Hostile civilians enter combat against their attacker (player, or NPC/mob) and alert their faction through
 *   {@link CivilianService#alertFaction}.</li>
 * </ul>
 * A player who dies or goes down is dropped as a target, and every squad hunting him is forgotten.
 */
@ListenerHandler
@RequiredArgsConstructor
public class CivilianDamageListener implements Listener {

	private final CivilianService civilianService;
	private final WitnessService  witnessService;

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCivilianDamage(EntityDamageByEntityEvent event) {
		Entity damaged = event.getEntity();

		CivilianNpc npc = civilianService.getNpc(damaged.getUniqueId());
		if (npc == null) return;

		Entity                   damager = event.getDamager();
		CivilianAIBehaviorConfig ai      = npc.getTypeConfig().ai();

		// Player attacker (direct hit or via projectile)
		Player playerAttacker = null;
		if (damager instanceof Player p && !NpcSupport.isNpc(p)) {
			playerAttacker = p;
		} else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p
		           && !NpcSupport.isNpc(p)) {
			playerAttacker = p;
		}

		if (playerAttacker != null) {
			if (npc.isHostile() && ai.combatEnabled()) {
				npc.setTargetPlayerId(playerAttacker.getUniqueId());
				if (npc.getCurrentState() != CivilianState.COMBAT) {
					npc.transitionTo(CivilianState.COMBAT);
				}
				civilianService.alertFaction(npc, playerAttacker, true);
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
				civilianService.alertFaction(npc, entityAttacker, false);
			} else if (!npc.isHostile() && ai.fleeEnabled()) {
				if (npc.getCurrentState() != CivilianState.FLEEING) {
					npc.setLastAttackerLocation(entityAttacker.getLocation().clone());
					npc.transitionTo(CivilianState.FLEEING);
				}
			}
		}
	}

	/**
	 * A guard whose role blocks takes {@code Block_Fraction} off a hit from inside its front cone.
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onShieldHit(EntityDamageByEntityEvent event) {
		CivilianNpc npc = civilianService.getNpc(event.getEntity().getUniqueId());
		if (npc == null) return;
		NpcRole role = npc.getRole();
		if (role.blockFraction() <= 0) return;

		// An arrow is judged by where its shooter stands, not where it lands
		Entity damager = event.getDamager();
		Entity source  = damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter
		                 ? shooter : damager;

		Location self = event.getEntity().getLocation();
		Location from = source.getLocation();
		double   dx   = from.getX() - self.getX();
		double   dz   = from.getZ() - self.getZ();
		double   length = Math.sqrt(dx * dx + dz * dz);
		double   angle  = 0.0; // point blank: whatever is in the guard's face
		if (length >= 1e-6) {
			double yaw = Math.toRadians(self.getYaw());
			double cos = (-Math.sin(yaw) * dx + Math.cos(yaw) * dz) / length;
			angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos))));
		}
		if (role.blocks(angle)) event.setDamage(event.getDamage() * (1.0 - role.blockFraction()));
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
		civilianService.dropSquads(playerId);
		for (CivilianNpc npc : civilianService.getActiveNpcs()) {
			if (playerId.equals(npc.getTargetPlayerId())) {
				npc.transitionTo(CivilianState.IDLE);
			}
		}
	}
}
