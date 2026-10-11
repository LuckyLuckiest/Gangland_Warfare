package org.luckyraven.gangland.gang.level;

import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.file.GangRules.LevelRowValues;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The level rows of {@code gang_rules.yml} {@code Levels}. It reads the rows through a supplier on every call, so an
 * edited row is live after {@code /glw reload} with no rebuild. A level with no row inherits the row before it (cost,
 * limits and prefix, but no commands, so a reward never repeats); with no row at all the answer is all zero, and
 * {@code 0} in a limit means unlimited.
 */
public class GangLevelTable {

	public record Row(int level, double cost, int maxMembers, int maxSafehouses, int maxAllies, String chatPrefix,
	                  List<String> commands) { }

	private volatile Supplier<Map<Integer, LevelRowValues>> source;

	public GangLevelTable() {
		this(Map::of);
	}

	public GangLevelTable(Supplier<Map<Integer, LevelRowValues>> source) {
		this.source = source;
	}

	public Row row(int level) {
		int                          wanted = Math.max(0, level);
		Map<Integer, LevelRowValues> rows   = source.get();

		LevelRowValues own = rows.get(wanted);
		if (own != null) {
			return of(wanted, own, own.commands());
		}

		int before = -1;
		for (int number : rows.keySet()) {
			if (number < wanted && number > before) before = number;
		}
		return before < 0 ? new Row(wanted, 0D, 0, 0, 0, "", List.of()) : of(wanted, rows.get(before), List.of());
	}

	/** Follows the rules object from now on; {@link GangRules#reload()} replaces its rows, which this then reads. */
	public void reload(GangRules rules) {
		source = rules::getLevelRows;
	}

	private static Row of(int level, LevelRowValues row, List<String> commands) {
		return new Row(level, row.cost(), row.maxMembers(), row.maxSafehouses(), row.maxAllies(), row.chatPrefix(),
		               commands);
	}
}
