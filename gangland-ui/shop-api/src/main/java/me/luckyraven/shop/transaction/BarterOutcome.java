package me.luckyraven.shop.transaction;

/**
 * Outcome categories returned by {@link ShopBarterService#barter}. Integrations map each outcome to a Messages key of
 * their choosing.
 */
public enum BarterOutcome {
	SUCCESS,
	NOT_BARTERABLE,
	MISSING_ITEMS
}
