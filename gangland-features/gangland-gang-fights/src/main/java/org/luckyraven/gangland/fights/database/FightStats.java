package org.luckyraven.gangland.fights.database;

/** {@code fight_stats}: one row per gang with its fight results. */
public record FightStats(int gangId, int wins, int losses, int draws) {

	public FightStats withWin() {
		return new FightStats(gangId, wins + 1, losses, draws);
	}

	public FightStats withLoss() {
		return new FightStats(gangId, wins, losses + 1, draws);
	}

	public FightStats withDraw() {
		return new FightStats(gangId, wins, losses, draws + 1);
	}
}
