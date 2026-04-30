package org.luckyraven.gangland.file.configuration.shop;

import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.shop.message.ShopMessageContract;

import java.math.BigDecimal;

/**
 * Default {@link ShopMessageContract} implementation. Routes every call through a {@link Messages} enum key and
 * substitutes placeholders; shop-api views/handlers never touch Messages directly.
 */
public final class GanglandShopMessages implements ShopMessageContract {

	@Override
	public String purchaseSuccess(String displayName, BigDecimal pricePaid) {
		return Messages.SHOP_PURCHASE_SUCCESS.toString()
		                                     .replace("%item%", displayName)
		                                     .replace("%money_symbol%", Settings.getMoneySymbol())
		                                     .replace("%price%", Settings.formatAmount(pricePaid));
	}

	@Override
	public String purchaseStackSuccess(String displayName, int quantity, BigDecimal totalPaid) {
		return Messages.SHOP_PURCHASE_STACK_SUCCESS.toString()
		                                           .replace("%item%", displayName)
		                                           .replace("%quantity%", String.valueOf(quantity))
		                                           .replace("%money_symbol%", Settings.getMoneySymbol())
		                                           .replace("%total%", Settings.formatAmount(totalPaid));
	}

	@Override
	public String purchaseInsufficientFunds(BigDecimal price) {
		return Messages.SHOP_PURCHASE_INSUFFICIENT_FUNDS.toString()
		                                                .replace("%money_symbol%", Settings.getMoneySymbol())
		                                                .replace("%price%", Settings.formatAmount(price));
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
	public String barterInsufficientValue(BigDecimal askingValue, BigDecimal offeredValue) {
		return Messages.SHOP_BARTER_INSUFFICIENT_VALUE.toString()
		                                              .replace("%money_symbol%", Settings.getMoneySymbol())
		                                              .replace("%asking%", Settings.formatAmount(askingValue))
		                                              .replace("%offered%", Settings.formatAmount(offeredValue));
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
	public String sellSuccess(BigDecimal totalPaid, int itemCount) {
		return Messages.SHOP_SELL_SUCCESS.toString()
		                                 .replace("%money_symbol%", Settings.getMoneySymbol())
		                                 .replace("%total%", Settings.formatAmount(totalPaid))
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
	public String shopAdminCategoryCreated(String id) {
		return Messages.SHOP_ADMIN_CATEGORY_CREATED.toString().replace("%id%", id);
	}

	@Override
	public String shopAdminCategoryRemoved(String id) {
		return Messages.SHOP_ADMIN_CATEGORY_REMOVED.toString().replace("%id%", id);
	}

}
