package org.luckyraven.gangland.copsncrooks.integration.detainment;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.economy.exception.EconomyException;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;

import java.math.BigDecimal;

import org.luckyraven.keystone.bean.Qualifier;
@CustomLog
public final class GanglandDetainmentEconomyContract implements DetainmentEconomyContract {

	private final UserManager<Player> userManager;

	public GanglandDetainmentEconomyContract(@Qualifier("online") UserManager<Player> userManager) {
		this.userManager = userManager;
	}

	@Override
	public ChargeResult tryCharge(Player player, double amount) {
		User<Player> user = userManager.getUser(player);
		if (user == null) return ChargeResult.ECONOMY_ERROR;

		EconomyHandler economy = user.getEconomy();
		BigDecimal     charge  = Currency.of(amount);
		if (economy.getAmount().compareTo(charge) < 0) return ChargeResult.INSUFFICIENT_FUNDS;

		try {
			economy.withdrawAmount(charge);
			return ChargeResult.SUCCESS;
		} catch (EconomyException e) {
			log.warn("Economy error during detainment charge for {}: {}", player.getName(), e.getMessage());
			return ChargeResult.ECONOMY_ERROR;
		}
	}

	@Override
	public double getBalance(Player player) {
		User<Player> user = userManager.getUser(player);
		if (user == null) return 0.0;
		return user.getEconomy().getAmount().doubleValue();
	}
}
