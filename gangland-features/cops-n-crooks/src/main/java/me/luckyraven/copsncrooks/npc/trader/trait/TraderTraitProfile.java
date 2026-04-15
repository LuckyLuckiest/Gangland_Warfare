package me.luckyraven.copsncrooks.npc.trader.trait;

public record TraderTraitProfile(
		int angerHitThreshold,
		double moodPerHit,
		double moodPerTipCurrency,
		double moodPerPurchase,
		double moodPerRejection,
		double moodDecayPerSecond,
		double maxAngerMultiplier,
		double minFriendDiscount,
		double bargainMinRatio,
		int bargainMaxRounds,
		boolean allowsBargaining,
		boolean allowsBarter,
		double sellPriceRatio,
		double maxHealth,
		boolean invulnerable
) {
}
