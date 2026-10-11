package org.luckyraven.gangland.fights.fight;

/** What a settled fight leaves behind. {@code winnerGangId} and {@code loserGangId} are -1 for a draw. */
public record FightResult(Fight fight, int winnerGangId, int loserGangId, long endedAt) {

	public boolean draw() {
		return winnerGangId < 0;
	}
}
