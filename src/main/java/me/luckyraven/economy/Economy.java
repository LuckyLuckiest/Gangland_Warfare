package me.luckyraven.economy;

import me.luckyraven.data.user.User;

/**
 * The type Economy.
 */
public class Economy {

	private final User<?> user;

	/**
	 * Instantiates a new Economy.
	 *
	 * @param user the type of account
	 */
	public Economy(User<?> user) {
		this.user = user;
	}

	/**
	 * Gets the user balance.
	 *
	 * @return the balance
	 */
	public double getBalance() {
		return user.getBalance();
	}

	/**
	 * Deposits the set amount into the user's account.
	 *
	 * @param amount the amount deposited
	 */
	public void deposit(double amount) {
		user.setBalance(getBalance() + amount);
	}

	/**
	 * Withdraws the set amount from the user's account.
	 *
	 * @param amount the amount withdrawn
	 */
	public void withdraw(double amount) {
		if (amount > getBalance()) throw new EconomyException("Amount exceeding balance");
		user.setBalance(getBalance() - amount);
	}

}
