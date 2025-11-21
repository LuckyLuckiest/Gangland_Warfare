package me.luckyraven.sign.aspect;

import me.luckyraven.data.economy.EconomyHandler;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.sign.model.ParsedSign;
import org.bukkit.entity.Player;

public class MoneyAspect implements SignAspect {

	private final UserManager<Player> userManager;
	private final TransactionType     transactionType;

	public MoneyAspect(UserManager<Player> userManager, TransactionType transactionType) {
		this.userManager     = userManager;
		this.transactionType = transactionType;
	}

	@Override
	public AspectResult execute(Player player, ParsedSign sign) {
		User<Player>   user    = userManager.getUser(player);
		EconomyHandler economy = user.getEconomy();
		double         amount  = sign.getPrice();

		if (transactionType == TransactionType.WITHDRAW) {
			if (economy.getBalance() < amount) {
				return AspectResult.failure("You don't have enough money!");
			}


			economy.withdraw(amount);

			return AspectResult.successContinue("You have withdrawn " + amount + " from your account!");
		} else {
			economy.deposit(amount);

			return AspectResult.successContinue("You have deposited " + amount + " to your account!");
		}
	}

	@Override
	public boolean canExecute(Player player, ParsedSign sign) {
		User<Player>   user    = userManager.getUser(player);
		EconomyHandler economy = user.getEconomy();

		if (transactionType == TransactionType.WITHDRAW) {
			return economy.getBalance() >= sign.getPrice();
		}

		return true;
	}

	@Override
	public String getName() {
		return "MoneyAspect-" + transactionType;
	}

	@Override
	public int getPriority() {
		return transactionType == TransactionType.WITHDRAW ? 100 : -100;
	}

	public enum TransactionType {
		WITHDRAW,
		DEPOSIT;
	}
}
