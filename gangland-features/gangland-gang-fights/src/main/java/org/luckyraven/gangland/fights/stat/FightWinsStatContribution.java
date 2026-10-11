package org.luckyraven.gangland.fights.stat;

import org.luckyraven.gangland.fights.FightRules;
import org.luckyraven.gangland.gang.stats.GangStatContribution;

/**
 * The {@code wins} gang ranking stat: ranked wins, at most Ranked_Board_Wins_Per_Opponent_Per_Day per opponent per
 * day.
 */
public final class FightWinsStatContribution implements GangStatContribution {

	private final FightHistory history;
	private final FightRules   rules;

	public FightWinsStatContribution(FightHistory history, FightRules rules) {
		this.history = history;
		this.rules   = rules;
	}

	@Override
	public String key() {
		return "wins";
	}

	@Override
	public long value(int gangId) {
		return history.boardWins(gangId, rules.getRankedBoardWinsPerOpponentPerDay());
	}
}
