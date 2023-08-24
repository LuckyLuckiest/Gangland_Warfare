package me.luckyraven.economy;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.data.user.User;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

public class EconomyHandler {

	private static @Getter
	@Setter Economy vaultEconomy;

	private final User<? extends OfflinePlayer> user;
	private final boolean                       useUser;

	private double balance;

	/**
	 * Instantiates a new Economy.
	 */
	public EconomyHandler(@Nullable User<? extends OfflinePlayer> user) {
		this(0D, user, user != null);
	}

	/**
	 * Instantiates a new Economy handler.
	 *
	 * @param balance the balance
	 */
	public EconomyHandler(double balance, @Nullable User<? extends OfflinePlayer> user, boolean useUser) {
		this.balance = balance;
		this.user = user;
		this.useUser = useUser;
	}

	/**
	 * Gets the user balance.
	 *
	 * @return the balance
	 */
	public double getBalance() {
		if (useUserInfo() && vaultEconomy != null) return vaultEconomy.getBalance(user.getUser());
		return balance;
	}

	public void setBalance(double amount) {
		this.balance = amount;

		if (!(useUserInfo() && vaultEconomy != null)) return;

		vaultEconomy.withdrawPlayer(user.getUser(), vaultEconomy.getBalance(user.getUser()));
		vaultEconomy.depositPlayer(user.getUser(), amount);
	}

	/**
	 * Deposits the set amount into the user's account.
	 *
	 * @param amount the amount deposited
	 */
	public void deposit(double amount) {
		setBalance(balance + amount);
	}

	/**
	 * Withdraws the set amount from the user's account.
	 *
	 * @param amount the amount withdrawn
	 */
	public void withdraw(double amount) {
		if (amount > getBalance()) throw new EconomyException("Amount exceeding balance");

		setBalance(balance - amount);
	}

	private boolean useUserInfo() {
		return user != null && useUser;
	}

}
