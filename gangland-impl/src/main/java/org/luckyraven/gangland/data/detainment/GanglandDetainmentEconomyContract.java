package org.luckyraven.gangland.data.detainment;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract;
import org.luckyraven.gangland.economy.Currency;
import org.luckyraven.gangland.economy.EconomyHandler;
import org.luckyraven.gangland.economy.exception.EconomyException;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;

import java.math.BigDecimal;

@CustomLog
public final class GanglandDetainmentEconomyContract implements DetainmentEconomyContract {

	private final UserManager<Player> userManager;

	public GanglandDetainmentEconomyContract(UserManager<Player> userManager) {
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
