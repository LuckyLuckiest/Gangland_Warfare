package org.luckyraven.gangland.copsncrooks.wanted.config;

/**
 * {@code Wanted.Charge_Sheet} of {@code copsncrooks/wanted.yml}: the fine on arrest, paid from the wallet only.
 *
 * @since 0.15.0
 */
public record ChargeSheetSettings(boolean enabled, double base, double perWantedLevel, double maximum,
                                  double secondsPerUnpaid, int maxExtraSeconds) {
	/** The shipped {@code Wanted.Charge_Sheet}. */
	public static final ChargeSheetSettings DEFAULT = new ChargeSheetSettings(true, 200, 250, 10000, 0.1, 600);


	/** The fine at {@code wantedLevel} stars. */
	public double fineFor(int wantedLevel) {
		return Math.min(maximum, base + Math.max(0, wantedLevel) * perWantedLevel);
	}

	/** Extra jail seconds for {@code unpaid} money the wallet could not cover. */
	public int extraSecondsFor(double unpaid) {
		return unpaid <= 0 ? 0 : (int) Math.min(maxExtraSeconds, Math.ceil(unpaid * secondsPerUnpaid));
	}
}
