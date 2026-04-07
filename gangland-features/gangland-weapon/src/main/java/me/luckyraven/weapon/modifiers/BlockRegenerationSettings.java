package me.luckyraven.weapon.modifiers;

/**
 * Provides block-regeneration tuning values to {@link BlockDamageManager}.
 * <p>
 * Implementations live in {@code gangland-impl} and delegate to {@code me.luckyraven.file.configuration.Settings},
 * keeping {@code gangland-weapon} decoupled from the main plugin's file-loading infrastructure.
 */
public interface BlockRegenerationSettings {

	/**
	 * Ticks between a block fully breaking and the moment it reappears in {@code RESTORE} mode.
	 */
	int getRestoreDelayTicks();

	/**
	 * Ticks between the last hit on a cracked block and the start of crack reverse-decay.
	 */
	int getRegenerationDelayTicks();

	/**
	 * Ticks between each crack-stage decrement during reverse-decay.
	 */
	int getRegenerationStepTicks();
}
