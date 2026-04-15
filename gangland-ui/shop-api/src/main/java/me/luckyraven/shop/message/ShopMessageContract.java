package me.luckyraven.shop.message;

/**
 * Message contract for the shop framework. Integrations provide an implementation that maps each call to their
 * preferred {@code Messages} enum value + placeholder substitution. Keeps shop-api decoupled from the gangland-impl
 * messages enum (same pattern as {@code TraderSettings} and other feature-module contracts).
 */
public interface ShopMessageContract {

	String purchaseSuccess(String displayName, double pricePaid);

	String purchaseInsufficientFunds(double price);

	String purchaseEconomyError(String detail);

	String barterSuccess(String displayName);

	String barterMissingItems(int amount, String material);

	String barterNotAllowed();

	String shopNotDefined(String shopKey);

	String shopSaved(String shopKey);

	String shopAdminEntryAdded(String material, int index, int page);

	String shopAdminEntryRemoved(int index);

}
