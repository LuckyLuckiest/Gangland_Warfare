package org.luckyraven.gangland.listener.gang.combat;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.luckyraven.gangland.gang.combat.AttackerResolver;
import org.luckyraven.gangland.gang.combat.CombatTag;
import org.luckyraven.gangland.gang.combat.DamageLedger;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Feeds the {@link DamageLedger} and the {@link CombatTag}. HIGH with {@code ignoreCancelled}: after the LOWEST
 * friendly-fire cancel (a blocked hit never tags or assists), and before the HIGHEST lethal-hit intercept of the
 * respawn system, so a one-shot kill is still recorded. A hit that does no damage (a snowball) never counts.
 */
@ListenerHandler
public class DamageLedgerListener implements Listener {

	private final DamageLedger ledger;
	private final CombatTag    combatTag;

	public DamageLedgerListener(DamageLedger ledger, CombatTag combatTag) {
		this.ledger    = ledger;
		this.combatTag = combatTag;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamage(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof Player victim)) return;

		Player attacker = AttackerResolver.resolve(event.getDamager());
		if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;

		double damage = event.getFinalDamage();
		if (damage <= 0D) return;

		ledger.record(attacker.getUniqueId(), victim.getUniqueId(), damage, System.currentTimeMillis());
		combatTag.tag(attacker.getUniqueId(), victim.getUniqueId());
	}
}
