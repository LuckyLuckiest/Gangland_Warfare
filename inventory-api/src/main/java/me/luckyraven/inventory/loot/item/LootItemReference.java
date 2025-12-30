package me.luckyraven.inventory.loot.item;

import lombok.Builder;
import lombok.Getter;

/**
 * Represents a reference to an item from existing configurations (weapons, ammo, etc.) Items are not defined here -
 * they are fetched from their respective managers
 */
@Getter
@Builder
public class LootItemReference {

	private final String       id;
	private final LootCategory category;
	private final String       referenceId;
	private final int          minAmount;
	private final int          maxAmount;
	private final double       weight;
	private final Rarity       rarity;
	private final String       tierRequirement;

	/**
	 * Gets the effective weight considering rarity
	 */
	public double getEffectiveWeight() {
		return rarity.calculateEffectiveWeight(weight);
	}

	/**
	 * Generates a random amount within the configured range
	 */
	public int generateAmount() {
		if (minAmount >= maxAmount) return minAmount;
		return minAmount + (int) (Math.random() * (maxAmount - minAmount + 1));
	}

	public enum LootCategory {
		WEAPON,
		AMMO,
		UNIQUE,
		REPAIR,
		CONSUMABLE,
		MATERIAL,
		MISC
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
