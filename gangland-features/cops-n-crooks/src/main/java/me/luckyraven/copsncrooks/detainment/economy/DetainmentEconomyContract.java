package me.luckyraven.copsncrooks.detainment.economy;

import org.bukkit.entity.Player;

/**
 * Thin contract letting detainment flows (in cops-n-crooks) charge a player without importing {@code UserManager} /
 * {@code EconomyHandler} directly.
 */
public interface DetainmentEconomyContract {

	ChargeResult tryCharge(Player player, double amount);

	double getBalance(Player player);

	enum ChargeResult {
		SUCCESS,
		INSUFFICIENT_FUNDS,
		ECONOMY_ERROR
	}
}
