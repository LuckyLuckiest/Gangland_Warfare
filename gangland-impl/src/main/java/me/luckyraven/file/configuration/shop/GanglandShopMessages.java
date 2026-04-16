package me.luckyraven.file.configuration.shop;

import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.shop.message.ShopMessageContract;

/**
 * Default {@link ShopMessageContract} implementation. Routes every call through a {@link Messages} enum key and
 * substitutes placeholders; shop-api views/handlers never touch Messages directly.
 */
public final class GanglandShopMessages implements ShopMessageContract {

	@Override
	public String purchaseSuccess(String displayName, double pricePaid) {
		return Messages.SHOP_PURCHASE_SUCCESS.toString()
		                                     .replace("%item%", displayName)
		                                     .replace("%money_symbol%", Settings.getMoneySymbol())
		                                     .replace("%price%", Settings.formatDouble(pricePaid));
	}

	@Override
	public String purchaseInsufficientFunds(double price) {
		return Messages.SHOP_PURCHASE_INSUFFICIENT_FUNDS.toString()
		                                                .replace("%money_symbol%", Settings.getMoneySymbol())
		                                                .replace("%price%", Settings.formatDouble(price));
	}

	@Override
	public String purchaseEconomyError(String detail) {
		return Messages.SHOP_PURCHASE_ECONOMY_ERROR.toString()
		                                           .replace("%detail%", detail == null ? "" : detail);
	}

	@Override
	public String barterSuccess(String displayName) {
		return Messages.SHOP_BARTER_SUCCESS.toString().replace("%item%", displayName);
	}

	@Override
	public String barterMissingItems() {
		return Messages.SHOP_BARTER_MISSING_ITEMS.toString();
	}

	@Override
	public String barterInsufficientValue(double askingValue, double offeredValue) {
		return Messages.SHOP_BARTER_INSUFFICIENT_VALUE.toString()
		                                              .replace("%money_symbol%", Settings.getMoneySymbol())
		                                              .replace("%asking%", Settings.formatDouble(askingValue))
		                                              .replace("%offered%", Settings.formatDouble(offeredValue));
	}

	@Override
	public String barterNotAllowed() {
		return Messages.SHOP_BARTER_NOT_ALLOWED.toString();
	}

	@Override
	public String shopNotDefined(String shopKey) {
		return Messages.SHOP_NOT_DEFINED.toString().replace("%shop%", shopKey);
	}

	@Override
	public String shopSaved(String shopKey) {
		return Messages.SHOP_SAVED.toString().replace("%shop%", shopKey);
	}

	@Override
	public String shopAdminEntryAdded(String material, int index, int page) {
		return Messages.SHOP_ADMIN_ENTRY_ADDED.toString()
		                                      .replace("%material%", material)
		                                      .replace("%index%", String.valueOf(index))
		                                      .replace("%page%", String.valueOf(page));
	}

	@Override
	public String shopAdminEntryRemoved(int index) {
		return Messages.SHOP_ADMIN_ENTRY_REMOVED.toString().replace("%index%", String.valueOf(index));
	}

	@Override
	public String sellSuccess(double totalPaid, int itemCount) {
		return Messages.SHOP_SELL_SUCCESS.toString()
		                                 .replace("%money_symbol%", Settings.getMoneySymbol())
		                                 .replace("%total%", Settings.formatDouble(totalPaid))
		                                 .replace("%count%", String.valueOf(itemCount));
	}

	@Override
	public String sellNothingValued() {
		return Messages.SHOP_SELL_NOTHING_VALUED.toString();
	}

	@Override
	public String sellEconomyError(String detail) {
		return Messages.SHOP_SELL_ECONOMY_ERROR.toString().replace("%detail%", detail == null ? "" : detail);
	}

	@Override
	public String tradeInSuccess(double credit, double owed) {
		return Messages.SHOP_TRADEIN_SUCCESS.toString()
		                                    .replace("%money_symbol%", Settings.getMoneySymbol())
		                                    .replace("%credit%", Settings.formatDouble(credit))
		                                    .replace("%owed%", Settings.formatDouble(owed));
	}

	@Override
	public String tradeInInsufficientFunds(double owed) {
		return Messages.SHOP_TRADEIN_INSUFFICIENT_FUNDS.toString()
		                                               .replace("%money_symbol%", Settings.getMoneySymbol())
		                                               .replace("%owed%", Settings.formatDouble(owed));
	}

	@Override
	public String tradeInInventoryFull() {
		return Messages.SHOP_TRADEIN_INVENTORY_FULL.toString();
	}

	@Override
	public String tradeInEconomyError(String detail) {
		return Messages.SHOP_TRADEIN_ECONOMY_ERROR.toString()
		                                          .replace("%detail%", detail == null ? "" : detail);
	}

	@Override
	public String tradeInRefund(double amount) {
		return Messages.SHOP_TRADEIN_REFUND.toString()
		                                   .replace("%money_symbol%", Settings.getMoneySymbol())
		                                   .replace("%amount%", Settings.formatDouble(amount));
	}

	@Override
	public String shopAdminCategoryCreated(String id) {
		return Messages.SHOP_ADMIN_CATEGORY_CREATED.toString().replace("%id%", id);
	}

	@Override
	public String shopAdminCategoryRemoved(String id) {
		return Messages.SHOP_ADMIN_CATEGORY_REMOVED.toString().replace("%id%", id);
	}

}
