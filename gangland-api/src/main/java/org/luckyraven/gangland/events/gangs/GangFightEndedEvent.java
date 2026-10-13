package org.luckyraven.gangland.events.gangs;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A gang fight ended. winnerGangId is -1 for a draw.
 *
 * @since gangland-api 2.6
 */
public class GangFightEndedEvent extends Event {

	private static final HandlerList handlers = new HandlerList();

	private final int winnerGangId;
	private final int loserGangId;
	private final double bet;
	private final boolean ranked;

	public GangFightEndedEvent(int winnerGangId, int loserGangId, double bet, boolean ranked) {
		this.winnerGangId = winnerGangId;
		this.loserGangId = loserGangId;
		this.bet = bet;
		this.ranked = ranked;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

	public int winnerGangId() {
		return winnerGangId;
	}

	public int loserGangId() {
		return loserGangId;
	}

	public double bet() {
		return bet;
	}

	public boolean ranked() {
		return ranked;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

}
