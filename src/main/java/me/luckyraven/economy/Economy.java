package me.luckyraven.economy;

import me.luckyraven.data.user.User;
import org.bukkit.OfflinePlayer;

/**
 * The type Economy.
 */
public class Economy {

	private final User<OfflinePlayer>                user;
	private final net.milkbowl.vault.economy.Economy vaultEconomy;

	/**
	 * Instantiates a new Economy.
	 *
	 * @param user the type of account
	 */
	public Economy(User<OfflinePlayer> user, net.milkbowl.vault.economy.Economy vaultEconomy) {
		this.user = user;
		this.vaultEconomy = vaultEconomy;
	}

	/**
	 * Gets the user balance.
	 *
	 * @return the balance
	 */
	public double getBalance() {
		if (vaultEconomy != null) return vaultEconomy.getBalance(user.getUser());
		return user.getBalance();
	}

	/**
	 * Deposits the set amount into the user's account.
	 *
	 * @param amount the amount deposited
	 */
	public void deposit(double amount) {
		if (vaultEconomy != null) vaultEconomy.depositPlayer(user.getUser(), amount);
		else user.setBalance(getBalance() + amount);
	}

	/**
	 * Withdraws the set amount from the user's account.
	 *
	 * @param amount the amount withdrawn
	 */
	public void withdraw(double amount) {
		if (amount > getBalance()) throw new EconomyException("Amount exceeding balance");

		if (vaultEconomy != null) vaultEconomy.withdrawPlayer(user.getUser(), amount);
		else user.setBalance(getBalance() - amount);
	}

}
