package me.luckyraven.weapon.modifiers;

import me.luckyraven.weapon.modifiers.action.BlockBreakModifier;

/**
 * Selects how the {@link BlockBreakModifier} treats a block once its hit threshold is reached.
 */
public enum BreakMode {

	/**
	 * Block fully breaks with break effects, then after the configured restore delay reappears at the maximum crack
	 * stage and reverse-decays back to its original state.
	 */
	RESTORE,

	/**
	 * Block never actually breaks. The crack overlay reaches the max stage and then reverse-decays back to clean.
	 */
	CRACK_ONLY,

	/**
	 * Block fully breaks and is never restored. Legacy permanent-destruction behavior.
	 */
	DESTROY
}
