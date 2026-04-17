package me.luckyraven.file.configuration.copsncrooks;

import lombok.CustomLog;
import me.luckyraven.copsncrooks.npc.trader.economy.TraderEconomyContract;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.economy.bank.EconomyException;
import me.luckyraven.economy.bank.EconomyHandler;
import org.bukkit.entity.Player;

@CustomLog
public final class GanglandTraderEconomy implements TraderEconomyContract {

	private final UserManager<Player> userManager;

	public GanglandTraderEconomy(UserManager<Player> userManager) {
		this.userManager = userManager;
	}

	@Override
	public TipResult tryTip(Player player, double amount) {
		User<Player> user = userManager.getUser(player);
		if (user == null) {
			return TipResult.ECONOMY_ERROR;
		}

		EconomyHandler economy = user.getEconomy();
		if (economy.getBalance() < amount) {
			return TipResult.INSUFFICIENT_FUNDS;
		}

		try {
			economy.withdraw(amount);
			return TipResult.SUCCESS;
		} catch (EconomyException e) {
			log.warn("Economy error during tip for {}: {}", player.getName(), e.getMessage());
			return TipResult.ECONOMY_ERROR;
		}
	}

}
