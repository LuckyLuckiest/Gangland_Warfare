package org.luckyraven.gangland.listener.gang;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.luckyraven.gangland.gang.combat.AttackerResolver;
import org.luckyraven.gangland.gang.combat.FriendlyFirePolicy;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Cancels player-vs-player damage and fire between gangmates and allies when {@link FriendlyFirePolicy} says so (fists,
 * arrows, pets, TNT, Flame and Fire Aspect). Runs at LOWEST so a blocked hit never reaches the damage ledger.
 */
@ListenerHandler
public class GangMembersDamageListener implements Listener {

	private final FriendlyFirePolicy policy;

	public GangMembersDamageListener(FriendlyFirePolicy policy) {
		this.policy = policy;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onGangMemberHitMembers(EntityDamageByEntityEvent event) {
		if (blocked(event.getDamager(), event.getEntity())) event.setCancelled(true);
	}

	/** GR-41: the burn that follows Flame or Fire Aspect has no damager, so it is stopped at the source. */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onCombust(EntityCombustByEntityEvent event) {
		if (blocked(event.getCombuster(), event.getEntity())) event.setCancelled(true);
	}

	private boolean blocked(Entity damager, Entity victim) {
		if (!(victim instanceof Player damaged)) return false;

		Player attacker = AttackerResolver.resolve(damager);
		if (attacker == null) return false;

		return policy.pvpBlocked(attacker.getUniqueId(), damaged.getUniqueId(), damaged.getLocation());
	}
}
