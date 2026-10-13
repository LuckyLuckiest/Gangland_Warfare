package org.luckyraven.gangland.fights.database;

import java.util.UUID;

/** {@code fight_player_stats}: one row per fighter. */
public record FightPlayerStats(UUID uuid, int wins, int losses, int kills) {

	public FightPlayerStats withWin() {
		return new FightPlayerStats(uuid, wins + 1, losses, kills);
	}

	public FightPlayerStats withLoss() {
		return new FightPlayerStats(uuid, wins, losses + 1, kills);
	}

	public FightPlayerStats withKills(int more) {
		return new FightPlayerStats(uuid, wins, losses, kills + more);
	}
}
