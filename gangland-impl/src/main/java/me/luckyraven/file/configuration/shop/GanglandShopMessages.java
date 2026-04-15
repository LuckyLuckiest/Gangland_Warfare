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
	public String barterMissingItems(int amount, String material) {
		return Messages.SHOP_BARTER_MISSING_ITEMS.toString()
		                                         .replace("%amount%", String.valueOf(amount))
		                                         .replace("%material%", material);
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

}
