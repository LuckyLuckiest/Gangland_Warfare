package me.luckyraven.turf.powerups;

/**
 * What an active turf buff actually does to the turf it's attached to. Each value is paired with a per-buff
 * {@code magnitude} read from {@code turf_powerups.yml} — the consumer of the buff (income distributor, capture
 * service, garrison purchase view) interprets that magnitude in its own units (multiplier, ratio, flat number).
 */
public enum EffectType {
	/**
	 * Multiplies the per-interval payout in {@code TurfIncomeDistributor}. Magnitude is the multiplier — e.g. 1.25 →
	 * 25% more income while the buff is active.
	 */
	INCOME_MULTIPLIER,
	/**
	 * Tilts the unclaimed-Phase-2 / owned-turf tug-of-war in the defender's favour. Magnitude is the bonus added to the
	 * defender side of the per-tick {@code net} count — e.g. 1.0 effectively adds one phantom defender.
	 */
	CAPTURE_DEFENSE_BONUS,
	/**
	 * Reduces the per-defender purchase cost in the garrison view while the buff is active. Magnitude is the multiplier
	 * applied to the base cost — e.g. 0.8 → defenders cost 20% less.
	 */
	GARRISON_DISCOUNT
}
