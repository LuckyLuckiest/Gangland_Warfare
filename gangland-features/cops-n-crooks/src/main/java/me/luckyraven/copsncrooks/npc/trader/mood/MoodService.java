package me.luckyraven.copsncrooks.npc.trader.mood;

import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitProfile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MoodService {

	private static final double MOOD_MIN              = -1.0D;
	private static final double MOOD_MAX              = 1.0D;
	private static final double HARD_REJECT_FLOOR     = -0.5D;
	private static final double HARD_OFFER_INSULT     = 0.5D;
	private static final double BARGAIN_RATIO_FLOOR   = 0.4D;
	private static final double BARGAIN_ROUND_PENALTY = 0.05D;
	private static final double BARGAIN_MOOD_WEIGHT   = 0.05D;

	// Propose-price thresholds. Trader buys low, sells high — the acceptable ceiling is *below* fair value so the
	// trader can resell at a profit. Soft ceiling allows for a counter; insult ratio trips hard rejection.
	private static final double PROPOSE_MARGIN_CEILING = 0.85D;
	private static final double PROPOSE_MARGIN_SOFT    = 1.10D;
	private static final double PROPOSE_INSULT_RATIO   = 1.50D;

	private final Map<UUID, Map<UUID, MoodState>> states = new HashMap<>();

	public double getMood(UUID traderId, UUID playerId) {
		MoodState state = getOrCreate(traderId, playerId);
		return state.getMood();
	}

	public void recordHit(UUID traderId, UUID attackerId, TraderTraitProfile trait) {
		adjust(traderId, attackerId, trait.moodPerHit());
	}

	public void recordTip(UUID traderId, UUID playerId, double amount, TraderTraitProfile trait) {
		adjust(traderId, playerId, amount * trait.moodPerTipCurrency());
	}

	public void recordPurchase(UUID traderId, UUID playerId, TraderTraitProfile trait) {
		adjust(traderId, playerId, trait.moodPerPurchase());
	}

	public void recordSale(UUID traderId, UUID playerId, double moodPerSale) {
		adjust(traderId, playerId, moodPerSale);
	}

	public BargainResult evaluateSellBargain(UUID traderId, UUID playerId, TraderTraitProfile trait, double baseOffer,
	                                         double requestedMultiplier, int roundsUsed) {
		if (!trait.allowsBargaining() || baseOffer <= 0) {
			return new BargainResult(BargainOutcome.REJECTED, baseOffer, 0);
		}

		int max = Math.max(0, trait.bargainMaxRounds() - roundsUsed);
		if (requestedMultiplier <= 1.0) {
			return new BargainResult(BargainOutcome.ACCEPTED, baseOffer, max);
		}

		double mood = getMood(traderId, playerId);

		double maxMultiplier = 1.0 + mood * BARGAIN_MOOD_WEIGHT - roundsUsed * BARGAIN_ROUND_PENALTY +
		                       (1.0 - trait.bargainMinRatio());
		maxMultiplier = Math.max(1.0, maxMultiplier);

		if (mood < HARD_REJECT_FLOOR || requestedMultiplier > maxMultiplier + HARD_OFFER_INSULT) {
			recordRejection(traderId, playerId, trait);
			return new BargainResult(BargainOutcome.HARD_REJECTED, baseOffer, 0);
		}

		if (requestedMultiplier <= maxMultiplier) {
			return new BargainResult(BargainOutcome.ACCEPTED, baseOffer * requestedMultiplier, max);
		}

		if (max > 0) {
			double counter = baseOffer * Math.max(1.0, (maxMultiplier + 1.0) / 2.0);
			return new BargainResult(BargainOutcome.COUNTER_OFFER, counter, max - 1);
		}

		recordRejection(traderId, playerId, trait);
		return new BargainResult(BargainOutcome.REJECTED, baseOffer, 0);
	}

	public void recordRejection(UUID traderId, UUID playerId, TraderTraitProfile trait) {
		adjust(traderId, playerId, trait.moodPerRejection());
	}

	/**
	 * Evaluate a player-proposed sell price (from the "Propose price" anvil input) against the trader's fair-value
	 * estimate. Stricter than {@link #evaluateSellBargain} — the trader needs a profit margin to resell, so the
	 * acceptable ceiling is intentionally below fair value. Does <strong>not</strong> consume a bargain round; the
	 * returned {@code remaining} reflects unused rounds only.
	 *
	 * @param proposedPrice the absolute price the player typed in
	 * @param fairValue the trader's internal estimate of the item's value (local valuator today, market median in a
	 * 		future PR)
	 */
	public BargainResult evaluateProposedSellPrice(UUID traderId, UUID playerId, TraderTraitProfile trait,
	                                               double proposedPrice, double fairValue) {
		if (fairValue <= 0 || proposedPrice <= 0) {
			return new BargainResult(BargainOutcome.REJECTED, 0, 0);
		}

		double mood      = getMood(traderId, playerId);
		int    remaining = Math.max(0, trait.bargainMaxRounds());
		double ratio     = proposedPrice / fairValue;

		if (mood < HARD_REJECT_FLOOR || ratio > PROPOSE_INSULT_RATIO) {
			recordRejection(traderId, playerId, trait);
			return new BargainResult(BargainOutcome.HARD_REJECTED, fairValue * PROPOSE_MARGIN_CEILING, remaining);
		}

		double moodShift         = mood * BARGAIN_MOOD_WEIGHT;
		double effectiveCeilTx   = Math.clamp(PROPOSE_MARGIN_CEILING + moodShift, BARGAIN_RATIO_FLOOR, 1.0);
		double acceptableCeiling = fairValue * effectiveCeilTx;

		if (proposedPrice <= acceptableCeiling) {
			return new BargainResult(BargainOutcome.ACCEPTED, proposedPrice, remaining);
		}

		if (ratio <= PROPOSE_MARGIN_SOFT) {
			return new BargainResult(BargainOutcome.COUNTER_OFFER, acceptableCeiling, remaining);
		}

		recordRejection(traderId, playerId, trait);
		return new BargainResult(BargainOutcome.REJECTED, acceptableCeiling, remaining);
	}

	public void clearTrader(UUID traderId) {
		states.remove(traderId);
	}

	public double priceMultiplier(UUID traderId, UUID playerId, TraderTraitProfile trait) {
		double mood = getMood(traderId, playerId);
		if (mood >= 0) {
			return 1.0 + (trait.minFriendDiscount() - 1.0) * mood;
		}
		return 1.0 + (trait.maxAngerMultiplier() - 1.0) * (-mood);
	}

	/**
	 * Evaluate a bargain offer against the <em>original</em> base price.
	 *
	 * <p>The trait's {@code bargainMinRatio} is interpreted as the minimum acceptable fraction of the
	 * base price (not of the current asking price). Each round already spent tightens the effective minimum, so
	 * repeated chipping away at an already-bargained price eventually gets rejected.</p>
	 *
	 * @param offerPrice the absolute price the player is offering
	 * @param basePrice the original, immutable price of the item
	 * @param roundsUsed how many bargain rounds the player has already submitted in this session
	 */
	public BargainResult evaluateBargain(UUID traderId, UUID playerId, TraderTraitProfile trait, double offerPrice,
	                                     double basePrice, int roundsUsed) {
		if (!trait.allowsBargaining() || basePrice <= 0) {
			return new BargainResult(BargainOutcome.REJECTED, basePrice, 0);
		}

		double mood            = getMood(traderId, playerId);
		double cumulativeRatio = offerPrice / basePrice;

		if (mood < HARD_REJECT_FLOOR || cumulativeRatio < HARD_OFFER_INSULT) {
			recordRejection(traderId, playerId, trait);
			return new BargainResult(BargainOutcome.HARD_REJECTED, basePrice, 0);
		}

		double effectiveMin = trait.bargainMinRatio() + mood * BARGAIN_MOOD_WEIGHT
		                      // friendlier → lower floor
		                      + roundsUsed * BARGAIN_ROUND_PENALTY;         // more rounds spent → higher floor
		effectiveMin = Math.clamp(effectiveMin, BARGAIN_RATIO_FLOOR, 1.0);

		int remaining = Math.max(0, trait.bargainMaxRounds() - roundsUsed);

		if (cumulativeRatio >= effectiveMin) {
			return new BargainResult(BargainOutcome.ACCEPTED, offerPrice, remaining);
		}

		if (remaining > 0) {
			// Counter drifts toward the trader's current minimum acceptable price, not toward full ask.
			double minAcceptable = basePrice * effectiveMin;
			double counterPrice  = Math.max(offerPrice, (offerPrice + minAcceptable) / 2.0);
			return new BargainResult(BargainOutcome.COUNTER_OFFER, counterPrice, remaining - 1);
		}

		recordRejection(traderId, playerId, trait);
		return new BargainResult(BargainOutcome.REJECTED, basePrice, 0);
	}

	public void decay(long nowMs, TraderTraitLookup lookup) {
		states.forEach((traderId, map) -> {
			TraderTraitProfile trait = lookup.apply(traderId);
			if (trait == null) return;

			map.values().forEach(state -> applyDecay(state, nowMs, trait.moodDecayPerSecond()));
		});
	}

	private void applyDecay(MoodState state, long nowMs, double decayPerSecond) {
		long elapsedMs = nowMs - state.getLastUpdateMs();
		if (elapsedMs <= 0) return;

		double seconds = elapsedMs / 1000.0;
		double delta   = decayPerSecond * seconds;
		double mood    = state.getMood();

		if (mood > 0) {
			mood = Math.max(0, mood - delta);
		} else if (mood < 0) {
			mood = Math.min(0, mood + delta);
		}

		state.setMood(mood);
		state.setLastUpdateMs(nowMs);
	}

	private void adjust(UUID traderId, UUID playerId, double delta) {
		MoodState state   = getOrCreate(traderId, playerId);
		double    updated = clamp(state.getMood() + delta);
		state.setMood(updated);
		state.setLastUpdateMs(System.currentTimeMillis());
	}

	private MoodState getOrCreate(UUID traderId, UUID playerId) {
		return states.computeIfAbsent(traderId, k -> new HashMap<>())
		             .computeIfAbsent(playerId, k -> new MoodState(System.currentTimeMillis()));
	}

	private double clamp(double value) {
		return Math.clamp(value, MOOD_MIN, MOOD_MAX);
	}

	@FunctionalInterface
	public interface TraderTraitLookup {
		TraderTraitProfile apply(UUID traderId);
	}

}
