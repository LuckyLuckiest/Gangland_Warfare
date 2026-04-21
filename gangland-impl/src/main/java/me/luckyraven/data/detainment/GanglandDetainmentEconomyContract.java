package me.luckyraven.data.detainment;

import lombok.CustomLog;
import me.luckyraven.copsncrooks.detainment.economy.DetainmentEconomyContract;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.economy.bank.Currency;
import me.luckyraven.economy.bank.EconomyException;
import me.luckyraven.economy.bank.EconomyHandler;
import org.bukkit.entity.Player;

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
