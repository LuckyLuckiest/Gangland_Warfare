package org.luckyraven.gangland.copsncrooks.bag;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.persistence.config.NodeReader;

/**
 * {@code Wanted.Bag} of {@code copsncrooks/wanted.yml}: the hot-cash bag.
 *
 * @param enable           {@code false} sends cash to the wallet as before 0.20.
 * @param fenceCut         share the fence keeps; strictly between 0 and 1.
 * @param maxPerChase      most cash one chase can bag; 0 = no cap.
 * @param maxBankedPerDay  most bag cash a getaway banks per server day; 0 = no cap.
 * @param spillStacks      money items a spilled bag drops (1 to 16).
 * @param spillVariation   {@code items/money.yml} variation the spilled bills look like.
 * @param hudEnable        show {@code Bag: $X} on the action bar.
 * @param hudIntervalTicks action bar refresh in ticks.
 * @since 0.20.0
 */
public record BagSettings(boolean enable, double fenceCut, double maxPerChase, double maxBankedPerDay, int spillStacks,
                          String spillVariation, boolean hudEnable, int hudIntervalTicks) {

	/** The shipped {@code Wanted.Bag}. */
	public static final BagSettings DEFAULT = new BagSettings(true, 0.35, 5000, 15000, 4, "medium", true, 40);

	/** Reads the {@code Wanted} section; a null section, or a missing block or key, is its default. */
	public static BagSettings parse(@Nullable NodeReader wanted) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
