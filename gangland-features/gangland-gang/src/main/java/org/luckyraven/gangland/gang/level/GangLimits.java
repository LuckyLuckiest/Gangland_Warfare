package org.luckyraven.gangland.gang.level;

import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;

import java.util.List;

/**
 * What a gang's level allows, read from the row of its current level. {@code 0} means unlimited, and so does an
 * unknown gang. The caps are grow-only: a gang above a cap (after an edit of the table) keeps what it has, only a new
 * member, safehouse or ally is refused, so callers compare the current count with these numbers and never shrink.
 */
public class GangLimits {

	private static final GangLevelTable.Row UNLIMITED = new GangLevelTable.Row(0, 0D, 0, 0, 0, "", List.of());

	private final GangLevelTable      table;
	private final GangLookupContract gangs;

	/** Neutral limits (everything unlimited): the seam default before the module wires the real table. */
	public GangLimits() {
		this(new GangLevelTable(), null);
	}

	public GangLimits(GangLevelTable table, GangLookupContract gangs) {
		this.table = table;
		this.gangs = gangs;
	}

	public int maxMembers(int gangId) {
		return currentRow(gangId).maxMembers();
	}

	public int maxSafehouses(int gangId) {
		return currentRow(gangId).maxSafehouses();
	}

	public int maxAllies(int gangId) {
		return currentRow(gangId).maxAllies();
	}

	public String chatPrefix(int gangId) {
		return currentRow(gangId).chatPrefix();
	}

	/** The fee of the next level; {@code 0} at the top level or for an unknown gang. */
	public double upgradeCost(int gangId) {
		Level level = level(gangId);
		return level == null || atTop(level) ? 0D : table.row(level.nextLevel()).cost();
	}

	/** XP still short of the next level's bar, never below 0; {@code 0} at the top level or for an unknown gang. */
	public double upgradeXpMissing(int gangId) {
		Level level = level(gangId);
		if (level == null || atTop(level)) return 0D;

		return Math.max(0D, level.experienceCalculation(level.nextLevel()) - level.getExperience());
	}

	private GangLevelTable.Row currentRow(int gangId) {
		Level level = level(gangId);
		return level == null ? UNLIMITED : table.row(level.getLevelValue());
	}

	private Level level(int gangId) {
		Gang gang = gangs == null ? null : gangs.findById(gangId);
		return gang == null ? null : gang.getLevel();
	}

	private static boolean atTop(Level level) {
		return level.getLevelValue() >= level.getMaxLevel();
	}
}
