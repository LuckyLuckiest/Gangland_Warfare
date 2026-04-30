package org.luckyraven.gangland.item.wearable;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Traits that can be applied to a {@link Wearable} armor piece. Each trait is analogous to a Minecraft enchantment but
 * operates within the Gangland damage pipeline rather than Minecraft's own armor formula.
 *
 * <p>Every trait has a {@link #maxLevel} cap and an {@link #effectPerLevel} that defines what one
 * level of the trait contributes.
 */
@Getter
@RequiredArgsConstructor
public enum WearableTrait {

	/**
	 * Reduces all incoming damage by a percentage. 5 % reduction per level (max 4 → up to 20 %).
	 */
	REINFORCED("reinforced", "Reduces all incoming damage", 4, 0.05),

	/**
	 * Provides extra reduction specifically against projectile damage (stacks on top of {@link #REINFORCED}). 4 %
	 * reduction per level (max 3 → up to 12 % additional).
	 */
	BULLETPROOF("bulletproof", "Reduces projectile damage", 3, 0.04),

	/**
	 * Provides extra reduction specifically against explosion damage (stacks on top of {@link #REINFORCED}). 8 %
	 * reduction per level (max 2 → up to 16 % additional).
	 */
	PADDED("padded", "Reduces explosion damage", 2, 0.08),

	/**
	 * Reduces the bonus damage dealt by critical hits. 10 % crit-bonus reduction per level (max 3 → up to 30 % crit
	 * reduction).
	 */
	TOUGHENED("toughened", "Reduces critical hit bonus damage", 3, 0.10),

	/**
	 * Reduces fire ticks applied to the wearer as a fraction of the original value. 25 % fire-tick reduction per level
	 * (max 2 → up to 50 % reduction).
	 */
	FIRE_RESISTANT("fire_resistant", "Reduces fire damage and duration", 2, 0.25),

	/**
	 * Gives the wearer a flat percentage chance per armor piece to completely nullify an incoming hit. 2 % chance per
	 * level per piece (max 3 → up to 6 % per piece).
	 */
	REACTIVE("reactive", "Chance to nullify an incoming hit", 3, 0.02),

	/**
	 * Purely cosmetic / descriptive trait - indicates the armor is lightweight. No combat effect.
	 */
	LIGHTWEIGHT("lightweight", "Reduces movement encumbrance", 2, 0.0),

	/**
	 * Reduces fuel consumption rate for fuel-powered gadgets. 10 % reduction per level (max 2 -> up to 20 %).
	 */
	FUEL_EFFICIENT("fuel_efficient", "Reduces fuel consumption", 2, 0.10);

	/**
	 * Config / NBT key used to identify this trait.
	 */
	private final String key;
	private final String description;
	private final int    maxLevel;
	/**
	 * The fractional effect contributed per level (interpretation depends on the trait).
	 */
	private final double effectPerLevel;

	/**
	 * Looks up a trait by its config key (case-insensitive).
	 *
	 * @param key the config key
	 *
	 * @return the matching trait, or {@code null} if not found
	 */
	public static WearableTrait fromKey(String key) {
		if (key == null) return null;
		for (WearableTrait trait : values()) {
			if (trait.key.equalsIgnoreCase(key)) return trait;
		}
		return null;
	}

}
