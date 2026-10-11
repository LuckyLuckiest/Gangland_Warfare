package org.luckyraven.gangland.gang.level;

/**
 * What a gang's level allows. Seam of the gang parity wave: the body belongs to lane W5-L; until then every limit is
 * {@code 0}, which means unlimited.
 */
public class GangLimits {

	public int maxMembers(int gangId) {
		return 0;
	}

	public int maxSafehouses(int gangId) {
		return 0;
	}

	public int maxAllies(int gangId) {
		return 0;
	}

	public String chatPrefix(int gangId) {
		return "";
	}

	public double upgradeCost(int gangId) {
		return 0D;
	}

	public double upgradeXpMissing(int gangId) {
		return 0D;
	}
}
