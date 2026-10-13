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

	// --- Car events and hotwire (0.20.0) ---

	/**
	 * Ticks between {@code CarMoveEvent}s while a car moves ({@code Car_Events.Move_Interval_Ticks}).
	 */
	int getCarMoveIntervalTicks();

	/**
	 * Whether a lockpick may hotwire a car the player may not use ({@code Hotwire.Enable}).
	 */
	boolean isHotwireEnabled();

	/**
	 * Seconds a hotwire takes ({@code Hotwire.Seconds}).
	 */
	int getHotwireSeconds();

	/**
	 * Blocks the thief may stray from the car before the hotwire stops ({@code Hotwire.Max_Distance}).
	 */
	double getHotwireMaxDistance();

	/**
	 * Whether a finished hotwire uses up the lockpick ({@code Hotwire.Consume_Lockpick}).
	 */
	boolean isHotwireConsumeLockpick();

	/**
	 * Unique item key that counts as the lockpick ({@code Hotwire.Lockpick_Item}).
	 */
	String getHotwireLockpickItem();

}
