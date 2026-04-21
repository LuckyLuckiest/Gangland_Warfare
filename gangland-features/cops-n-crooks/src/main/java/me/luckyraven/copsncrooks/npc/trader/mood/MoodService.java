package me.luckyraven.copsncrooks.npc.trader.mood;

import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitProfile;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MoodService {

	private static final double MOOD_MIN = 0.0D;
	private static final double MOOD_MAX = 1.0D;

	private final Map<UUID, Map<UUID, MoodState>> states = new HashMap<>();

	public double getMood(UUID traderId, UUID playerId) {
		MoodState state = getOrCreate(traderId, playerId);
		return state.getMood();
	}

	public void recordTip(UUID traderId, UUID playerId, BigDecimal amount, TraderTraitProfile trait) {
		adjust(traderId, playerId, amount.doubleValue() * trait.moodPerTipCurrency());
	}

	public void recordPurchase(UUID traderId, UUID playerId, TraderTraitProfile trait) {
		adjust(traderId, playerId, trait.moodPerPurchase());
	}

	public void recordSale(UUID traderId, UUID playerId, double moodPerSale) {
		adjust(traderId, playerId, moodPerSale);
	}

	public void clearTrader(UUID traderId) {
		states.remove(traderId);
	}

	public double priceMultiplier(UUID traderId, UUID playerId, TraderTraitProfile trait) {
		double mood = getMood(traderId, playerId);
		return 1.0 + (trait.minFriendDiscount() - 1.0) * mood;
	}

	private void adjust(UUID traderId, UUID playerId, double delta) {
		MoodState state   = getOrCreate(traderId, playerId);
		double    updated = clamp(state.getMood() + delta);
		state.setMood(updated);
	}

	private MoodState getOrCreate(UUID traderId, UUID playerId) {
		return states.computeIfAbsent(traderId, k -> new HashMap<>())
		             .computeIfAbsent(playerId, k -> new MoodState());
	}

	private double clamp(double value) {
		return Math.clamp(value, MOOD_MIN, MOOD_MAX);
	}

}
