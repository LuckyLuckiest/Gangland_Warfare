package me.luckyraven.copsncrooks.listener.civilian;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.civilian.CivilianNpc;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.civilian.CivilianState;
import me.luckyraven.copsncrooks.npc.civilian.config.CivilianAIBehaviorConfig;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

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
		Entity damaged = event.getEntity();

		CivilianNpc npc = civilianService.getNpc(damaged.getUniqueId());
		if (npc == null) return;

		Entity damager = event.getDamager();
		if (!(damager instanceof Player attacker)) return;

		CivilianAIBehaviorConfig ai = npc.getTypeConfig().ai();

		if (npc.isHostile() && ai.combatEnabled()) {
			npc.setTargetPlayerId(attacker.getUniqueId());
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
}
