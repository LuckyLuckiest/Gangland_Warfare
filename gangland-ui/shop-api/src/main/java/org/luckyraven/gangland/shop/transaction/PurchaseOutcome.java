package org.luckyraven.gangland.shop.transaction;

/**
 * Outcome categories returned by {@link ShopPurchaseService#purchase}. Integrations map each outcome to whatever
 * user-facing message is appropriate for their shop flow.
 */
public enum PurchaseOutcome {
	SUCCESS,
	INSUFFICIENT_FUNDS,
	ECONOMY_ERROR,
	INVENTORY_FULL
}
