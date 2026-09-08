package org.luckyraven.gangland.copsncrooks.npc.trader.trait;

public record TraderTraitProfile(
		double moodPerTipCurrency,
		double moodPerPurchase,
		double minFriendDiscount,
		boolean allowsBarter,
		double sellPriceRatio,
		double barterPriceRatio,
		double maxHealth,
		boolean invulnerable
) {
}
