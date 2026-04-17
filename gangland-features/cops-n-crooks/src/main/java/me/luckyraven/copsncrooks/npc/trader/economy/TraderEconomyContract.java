package me.luckyraven.copsncrooks.npc.trader.economy;

import org.bukkit.entity.Player;

/**
 * Thin contract letting trader views (which live in cops-n-crooks) delegate currency withdrawals to the host module
 * (gangland-impl) without importing {@code UserManager} / {@code EconomyHandler} directly.
 */
public interface TraderEconomyContract {

	TipResult tryTip(Player player, double amount);

	enum TipResult {
		SUCCESS,
		INSUFFICIENT_FUNDS,
		ECONOMY_ERROR
	}

}
