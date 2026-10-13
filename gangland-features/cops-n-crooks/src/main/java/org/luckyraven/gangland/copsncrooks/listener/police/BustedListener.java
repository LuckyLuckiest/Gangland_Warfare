package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.wanted.WantedClearContract;
import org.luckyraven.gangland.copsncrooks.integration.detainment.DetainmentSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * 0.21.0 "Busted or wasted": a lethal hit on a wanted player next to a hunting cop of his own squad becomes an arrest.
 * TDD stub: behaviour lands in the green chunks.
 */
@ListenerHandler
public class BustedListener implements Listener {

	/** getNearbyEntities bound; ponytail: covers the widest shipped Cuff_Radius (5.0), the exact per-cop check follows. */
	static final double SEARCH_BOUND = 6.0;

	public BustedListener(CopManager copManager, CopLoader copLoader, DetainmentService detainmentService,
	                      DetainmentSettings detainmentSettings, CuffLockRegistry cuffLockRegistry,
	                      WantedClearContract wantedClearContract, WantedMessages wantedMessages) {
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}

}
