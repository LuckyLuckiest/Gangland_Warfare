package org.luckyraven.gangland.fights.stat;

import org.luckyraven.gangland.fights.database.FightStats;
import org.luckyraven.gangland.fights.placeholder.FightPlaceholderContribution;

import java.util.List;

/** The two lines of {@code /glw gang fight stats}. */
public final class FightStatsReport {

	private FightStatsReport() {
	}

	public static List<String> lines(FightTexts texts, String gangName, FightStats stats) {
		return List.of(texts.get("Stats.Header").replace("%gang%", gangName),
		               texts.get("Stats.Line")
		                    .replace("%wins%", String.valueOf(stats.wins()))
		                    .replace("%losses%", String.valueOf(stats.losses()))
		                    .replace("%draws%", String.valueOf(stats.draws()))
		                    .replace("%wlr%", FightPlaceholderContribution.wlr(stats)));
	}
}
