package org.luckyraven.gangland.gang.level;

import org.luckyraven.gangland.gang.file.GangRules;

import java.util.List;

/**
 * The level rows of {@code gang_rules.yml} {@code Levels}. Seam: the body belongs to lane W5-L; until then every row is
 * zero.
 */
public class GangLevelTable {

	public record Row(int level, double cost, int maxMembers, int maxSafehouses, int maxAllies, String chatPrefix,
	                  List<String> commands) { }

	public Row row(int level) {
		return new Row(level, 0D, 0, 0, 0, "", List.of());
	}

	public void reload(GangRules rules) {
		// W5-L builds the table from the rules
	}
}
