package org.luckyraven.gangland.fights.database;

/** {@code fight_record}: one finished fight. {@code winner} is -1 for a draw. */
public record FightRecord(long id, int gangA, int gangB, int winner, double bet, boolean ranked, long endedAt) {

	public boolean draw() {
		return winner < 0;
	}

	public int loser() {
		return winner == gangA ? gangB : winner == gangB ? gangA : -1;
	}
}
