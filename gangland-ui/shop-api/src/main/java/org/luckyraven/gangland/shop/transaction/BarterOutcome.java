package org.luckyraven.gangland.shop.transaction;

/**
 * Outcome categories returned by {@link ShopBarterService#barter}. Integrations map each outcome to a Messages key of
 * their choosing. Barter is a pure item-for-item swap — no economy money is ever involved.
 */
public enum BarterOutcome {
	SUCCESS,
	NOT_BARTERABLE,
	MISSING_ITEMS,
	INSUFFICIENT_VALUE
}
