package org.luckyraven.gangland.fights.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.fights.fight.Fight;
import org.luckyraven.gangland.fights.fight.FightManager;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Defence in depth for GP-41: {@link FightDamageListener} already stops the lethal-hit path that raises wanted, but a
 * stray hit on a civilian or a cop in the arena must not start a cop hunt either. Cancels any raise for a player whose
 * fight has them in the arena (countdown, running, result). Lowering is left alone.
 */
@ListenerHandler
public final class FightWantedGuard implements Listener {

	private final FightManager manager;

	public FightWantedGuard(FightManager manager) {
		this.manager = manager;
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onWantedChange(WantedLevelChangeEvent event) {
		if (event.getNewLevel() <= event.getOldLevel()) return;

		Fight fight = manager.fightOf(event.getPlayer().getUniqueId()).orElse(null);
		if (fight != null && Fighters.inArena(fight)) {
			event.setCancelled(true);
		}
	}
}
