package org.luckyraven.gangland.copsncrooks.npc.trader.economy;

import org.bukkit.entity.Player;

import java.math.BigDecimal;

/**
 * Thin contract letting trader views (which live in cops-n-crooks) delegate currency withdrawals to the host module
 * (gangland-impl) without importing {@code UserManager} / {@code EconomyHandler} directly.
 */
public interface TraderEconomyContract {

	TipResult tryTip(Player player, BigDecimal amount);

	enum TipResult {
		SUCCESS,
		INSUFFICIENT_FUNDS,
		ECONOMY_ERROR
	}

}
