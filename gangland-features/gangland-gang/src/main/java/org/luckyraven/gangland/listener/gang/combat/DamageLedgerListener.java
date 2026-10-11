package org.luckyraven.gangland.listener.gang.combat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.luckyraven.gangland.gang.combat.CombatTag;
import org.luckyraven.gangland.gang.combat.DamageLedger;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/** RED stub. */
@ListenerHandler
public class DamageLedgerListener implements Listener {

	public DamageLedgerListener(DamageLedger ledger, CombatTag combatTag) {
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamage(EntityDamageByEntityEvent event) {
	}
}
