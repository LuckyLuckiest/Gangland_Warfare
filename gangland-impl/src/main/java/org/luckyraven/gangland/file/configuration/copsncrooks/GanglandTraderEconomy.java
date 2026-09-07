package org.luckyraven.gangland.file.configuration.copsncrooks;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.npc.trader.economy.TraderEconomyContract;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.economy.exception.EconomyException;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;

import java.math.BigDecimal;

@CustomLog
public final class GanglandTraderEconomy implements TraderEconomyContract {

	private final UserManager<Player> userManager;

	public GanglandTraderEconomy(UserManager<Player> userManager) {
		this.userManager = userManager;
	}

	@Override
	public TipResult tryTip(Player player, BigDecimal amount) {
		User<Player> user = userManager.getUser(player);
		if (user == null) {
			return TipResult.ECONOMY_ERROR;
		}

		EconomyHandler economy = user.getEconomy();
		if (economy.getAmount().compareTo(amount) < 0) {
			return TipResult.INSUFFICIENT_FUNDS;
		}

		try {
			economy.withdrawAmount(amount);
			return TipResult.SUCCESS;
		} catch (EconomyException e) {
			log.warn("Economy error during tip for {}: {}", player.getName(), e.getMessage());
			return TipResult.ECONOMY_ERROR;
		}
	}

}
