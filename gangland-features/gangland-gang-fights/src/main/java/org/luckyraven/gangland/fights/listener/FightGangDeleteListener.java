package org.luckyraven.gangland.fights.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.events.gang.GangDeleteEvent;
import org.luckyraven.gangland.fights.fight.Fight;
import org.luckyraven.gangland.fights.fight.FightManager;
import org.luckyraven.gangland.fights.fight.Participant;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.ArrayList;

/**
 * Disbanding to escape a fight is a forfeit: every fighter of the deleted gang is forfeited, so the other side wins
 * the settlement (and with it the escrow) in a running fight, or the fight is cancelled and refunded in the
 * countdown. The event fires after the gang is gone, so only its id is read.
 */
@ListenerHandler
public final class FightGangDeleteListener implements Listener {

	private final FightManager manager;

	public FightGangDeleteListener(FightManager manager) {
		this.manager = manager;
	}

	@EventHandler
	public void onGangDelete(GangDeleteEvent event) {
		int gangId = event.getGang().getId();
		for (Fight fight : manager.active()) {
			if (fight.gangA() != gangId && fight.gangB() != gangId) continue;

			for (Participant participant : new ArrayList<>(fight.participants())) {
				if (participant.gangId() == gangId) {
					manager.forfeit(participant.player(), "gang_deleted");
				}
			}
		}
	}
}
