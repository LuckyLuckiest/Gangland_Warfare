package org.luckyraven.gangland.gadget.config;

/**
 * Physics constants for gadget modules (jetpack, car) that are loaded from {@code settings.yml}. Implementations live
 * in {@code gangland-impl} and are injected into the respective services.
 */
public interface GadgetPhysicsConfig {

	// --- Jetpack ---

	/**
	 * Ticks of continuous thrust to ramp from 10 % to 100 % ascend power.
	 */
	int getJetpackThrustRampTicks();

	/**
	 * Downward acceleration added per tick while descending (m/tick²).
	 */
	double getJetpackDescentAccel();

	/**
	 * Terminal descent speed — descent will not exceed this (negative = downward).
	 */
	double getJetpackMaxDescentSpeed();

	/**
	 * Horizontal speed delta added per tick when a directional key is held (m/tick).
	 */
	double getJetpackHorizInfluence();

	/**
	 * Maximum horizontal speed the jetpack allows while airborne (m/tick).
	 */
	double getJetpackMaxHorizSpeed();

	// --- Car ---

	/**
	 * Fraction of max speed applied when reversing (e.g. 0.5 = half max speed).
	 */
	double getCarReverseSpeedRatio();

	/**
	 * Multiplier applied to deceleration when the driver hard-brakes (sneak key).
	 */
	double getCarHardBrakeMultiplier();

	/**
	 * Fuel units consumed per tick while the car is moving.
	 */
	int getCarFuelConsumePerTick();

}
