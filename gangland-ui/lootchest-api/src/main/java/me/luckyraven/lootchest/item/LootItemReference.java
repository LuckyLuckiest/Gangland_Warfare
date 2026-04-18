package me.luckyraven.lootchest.item;

import lombok.Builder;
import lombok.Getter;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A loot table entry. Holds only loot-level metadata (weight, rarity, tier, amount range) — item identity is stored as
 * an {@code itemString} resolved through the global {@code ItemParser} at roll time, so every converter registered with
 * the parser is automatically usable here.
 */
@Getter
@Builder
public class LootItemReference {

	private final String id;
	private final String itemString;
	private final int    minAmount;
	private final int    maxAmount;
	private final double weight;
	private final Rarity rarity;

	/**
	 * Gets the effective weight considering rarity
	 */
	public double getEffectiveWeight() {
		return rarity.calculateEffectiveWeight(weight);
	}

	/**
	 * Generates a random amount within the configured range (inclusive).
	 */
	public int generateAmount() {
		if (maxAmount <= minAmount) {
			return Math.max(1, minAmount);
		}

		return ThreadLocalRandom.current().nextInt(minAmount, maxAmount + 1);
	}

	public enum Rarity {
		COMMON(1.0, "&7"),
		UNCOMMON(0.7, "&a"),
		RARE(0.4, "&9"),
		EPIC(0.15, "&5"),
		LEGENDARY(0.05, "&6");

		@Getter
		private final double spawnMultiplier;
		@Getter
		private final String colorPrefix;

		Rarity(double spawnMultiplier, String colorPrefix) {
			this.spawnMultiplier = spawnMultiplier;
			this.colorPrefix     = colorPrefix;
		}

		/**
		 * Calculates the effective weight based on rarity
		 */
		public double calculateEffectiveWeight(double baseWeight) {
			return baseWeight * spawnMultiplier;
		}

		/**
		 * Determines if this rarity should spawn based on random roll
		 */
		public boolean shouldSpawn() {
			return Math.random() <= spawnMultiplier;
		}
	}

}
