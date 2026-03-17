package me.luckyraven.sign.aspect;

import lombok.RequiredArgsConstructor;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.economy.EconomyException;
import me.luckyraven.data.economy.EconomyHandler;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.sign.model.ParsedSign;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public class MoneyAspect implements SignAspect {

	private final UserManager<Player> userManager;
	private final TransactionType     transactionType;

	@Override
	public AspectResult execute(Player player, ParsedSign sign) {
		User<Player> user = userManager.getUser(player);

		if (user == null) return AspectResult.failure(Messages.PLAYER_NOT_FOUND.toString());

		EconomyHandler economy = user.getEconomy();
		double         amount  = sign.getPrice();

		if (transactionType == TransactionType.WITHDRAW) {
			if (amount == 0D) {
				String string = Messages.FREE_TRANSACTION.toString(Messages.Type.NO_CHANGE);
				return AspectResult.successContinue(string);
			}

			try {
				economy.withdraw(amount);
			} catch (EconomyException exception) {
				return AspectResult.failure(exception.getMessage());
			}

			String withdrawn = Messages.WITHDRAW_MONEY_PLAYER.toString(Messages.Type.NO_CHANGE);
			return AspectResult.success(withdrawn.replace("%amount%", SettingAddon.formatDouble(amount)));
		} else {
			economy.deposit(amount);

			String deposit = Messages.DEPOSIT_MONEY_PLAYER.toString(Messages.Type.NO_CHANGE);
			return AspectResult.successContinue(deposit.replace("%amount%", SettingAddon.formatDouble(amount)));
		}
	}

	@Override
	public boolean canExecute(Player player, ParsedSign sign) {
		User<Player> user = userManager.getUser(player);

		if (user == null) return false;

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
