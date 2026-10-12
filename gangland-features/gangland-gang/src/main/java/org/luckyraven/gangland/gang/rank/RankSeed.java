package org.luckyraven.gangland.gang.rank;

import lombok.CustomLog;
import org.luckyraven.gangland.gang.database.repositories.rank.RankParentRepository;
import org.luckyraven.gangland.gang.database.repositories.rank.RankRepository;
import org.luckyraven.gangland.gang.file.GangRules;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the preset rank ladder ({@code gang/gang_rules.yml} {@code Ranks.Preset}) on a fresh install (D13 A). It runs
 * only while the rank table is empty, so an existing rank tree is never changed.
 */
@CustomLog
public final class RankSeed {

	private RankSeed() {
	}

	/**
	 * Seeds the preset ladder when the rank table is empty; does nothing otherwise. Ranks are written lowest first with
	 * ids {@code 1..n}, and each rank is linked to the next one above it. When the preset's first or last entry does not
	 * match {@code head}/{@code tail}, one warning is logged and the configured names are used at the two ends.
	 *
	 * @return {@code true} when the ladder was seeded
	 */
	public static boolean seedIfEmpty(RankRepository ranks, RankParentRepository parents, GangRules rules, String head,
	                                  String tail) throws SQLException {
		if (ranks.totalRanks() != 0) return false;

		List<String> ladder = new ArrayList<>(rules.getRankPreset());
		int          last   = ladder.size() - 1;

		if (!ladder.get(0).equalsIgnoreCase(head) || !ladder.get(last).equalsIgnoreCase(tail)) {
			log.warn("gang_rules.yml Ranks.Preset ends ({}, {}) differ from Rank.Head/Rank.Tail ({}, {}); seeding {} and {} at the ends",
			         ladder.get(0), ladder.get(last), head, tail, head, tail);
			ladder.set(0, head);
			ladder.set(last, tail);
		}

		for (int i = 0; i <= last; i++) {
			ranks.insertNow(new Rank(ladder.get(i), i + 1));
		}
		for (int i = 0; i < last; i++) {
			parents.insertInitialRelation(i + 1, i + 2);
		}

		return true;
	}

}
