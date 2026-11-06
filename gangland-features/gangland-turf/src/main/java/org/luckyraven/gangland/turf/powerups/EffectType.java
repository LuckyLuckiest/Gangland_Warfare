package org.luckyraven.gangland.turf.powerups;

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
	 * Retired in 0.17.0, kept only so old rows load; guards on the capture bar replaced it.
	 */
	CAPTURE_DEFENSE_BONUS,
	/**
	 * Reduces the per-defender purchase cost in the garrison view while the buff is active. Magnitude is the multiplier
	 * applied to the base cost — e.g. 0.8 → defenders cost 20% less.
	 */
	GARRISON_DISCOUNT,
	/**
	 * Multiplies the health of the turf's guards, standing ones too. Magnitude is the multiplier, at least 1 (1.5 means
	 * half again as much health). Stacked buffs multiply, clamped by {@code Limits.Max_Guard_Toughness}.
	 */
	GUARD_TOUGHNESS,
	/**
	 * Multiplies the contest wave size and the most guards alive at once. Magnitude is the multiplier, at least 1. It
	 * only ever spends guards the turf already owns. Clamped by {@code Limits.Max_Guard_Waves}.
	 */
	BIGGER_GUARD_WAVES,
	/**
	 * Multiplies the wait before replacement guards arrive, in a contest and outside one. Magnitude is in (0, 1]: 0.5
	 * halves the wait. Clamped by {@code Limits.Min_Reinforcement_Wait}.
	 */
	FASTER_REINFORCEMENTS
}
