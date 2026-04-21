package me.luckyraven.file.configuration.copsncrooks;

import me.luckyraven.copsncrooks.npc.trader.message.TraderMessageContract;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;

import java.math.BigDecimal;

/**
 * Default {@link TraderMessageContract} implementation. Routes every call through a {@link Messages} enum key and
 * substitutes placeholders so cops-n-crooks trader code stays decoupled from the Messages enum.
 */
public final class GanglandTraderMessages implements TraderMessageContract {

	@Override
	public String tipSuccess(BigDecimal amount) {
		return Messages.TRADER_TIP_SUCCESS.toString()
		                                  .replace("%money_symbol%", Settings.getMoneySymbol())
		                                  .replace("%amount%", Settings.formatAmount(amount));
	}

	@Override
	public String tipInsufficientFunds(BigDecimal amount) {
		return Messages.TRADER_TIP_INSUFFICIENT_FUNDS.toString()
		                                             .replace("%money_symbol%", Settings.getMoneySymbol())
		                                             .replace("%amount%", Settings.formatAmount(amount));
	}

	@Override
	public String traitInvalid() {
		return Messages.TRADER_TRAIT_INVALID.toString();
	}

}
