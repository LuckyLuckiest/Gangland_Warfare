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

	public BagSettings {
		spillStacks = Math.max(1, Math.min(16, spillStacks));
	}

	/** Reads the {@code Wanted} section; a null section, or a missing block or key, is its default. */
	public static BagSettings parse(@Nullable NodeReader wanted) {
		if (wanted == null) return DEFAULT;

		NodeReader block = wanted.get("Bag").asMapping().reader();
		if (block == null) return DEFAULT;

		// ponytail: nextUp/nextDown make the 0 and 1 bounds exclusive; the range report shows the epsilon
		return new BagSettings(block.get("Enable").asBool().orDefault(DEFAULT.enable()),
		                       block.get("Fence_Cut").asDouble().min(Math.nextUp(0.0)).max(Math.nextDown(1.0))
		                            .orDefault(DEFAULT.fenceCut()),
		                       block.get("Max_Per_Chase").asDouble().min(0).orDefault(DEFAULT.maxPerChase()),
		                       block.get("Max_Banked_Per_Day").asDouble().min(0).orDefault(DEFAULT.maxBankedPerDay()),
		                       block.get("Spill_Stacks").asInt().orDefault(DEFAULT.spillStacks()),
		                       block.get("Spill_Variation").asString().orDefault(DEFAULT.spillVariation()),
		                       block.get("Hud_Enable").asBool().orDefault(DEFAULT.hudEnable()),
		                       block.get("Hud_Interval_Ticks").asInt().min(1).orDefault(DEFAULT.hudIntervalTicks()));
	}
}
