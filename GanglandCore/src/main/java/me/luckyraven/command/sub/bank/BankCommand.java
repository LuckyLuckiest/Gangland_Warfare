package me.luckyraven.command.sub.bank;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.account.Account;
import me.luckyraven.data.account.type.Bank;
import me.luckyraven.data.user.User;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BankCommand extends CommandHandler {

	public BankCommand(Gangland gangland) {
		super(gangland, "bank", true);

		List<CommandInformation> list = getCommands().entrySet()
													 .stream()
													 .filter(entry -> entry.getKey().startsWith("bank"))
													 .sorted(Map.Entry.comparingByKey())
													 .map(Map.Entry::getValue)
													 .toList();
		getHelpInfo().addAll(list);
	}

	static void processMoney(User<Player> user, Bank bank, double check, double amount, double inBank,
							 double inAccount) {
		if (check == 0D) user.getUser().sendMessage(MessageAddon.CANNOT_TAKE_LESS_THAN_ZERO.toString());
		else if (amount > check) user.getUser().sendMessage(MessageAddon.CANNOT_TAKE_MORE_THAN_BALANCE.toString());
		else {
			user.getEconomy().setBalance(inAccount);
			bank.getEconomy().setBalance(inBank);
		}
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		Player       player = (Player) commandSender;
		User<Player> user   = getGangland().getInitializer().getUserManager().getUser(player);

		if (!user.hasBank()) {
			help(commandSender, 1);
			return;
		}

		for (Account<?, ?> account : user.getLinkedAccounts()) {
			if (!(account instanceof Bank bank)) continue;

			player.sendMessage(ChatUtil.color(String.format("&6%s&7 bank information", player.getName()),
											  String.format("&7%s&8: &a%s", "Name", bank.getName()),
											  String.format("&7%s&8: &a%s%s", "Balance", SettingAddon.getMoneySymbol(),
															SettingAddon.formatDouble(
																	bank.getEconomy().getBalance()))));
			break;
		}
	}

	@Override
	protected void initializeArguments() {
		BankCreateCommand   create   = new BankCreateCommand(getGangland(), getArgumentTree(), getArgument());
		BankDeleteCommand   delete   = new BankDeleteCommand(getGangland(), getArgumentTree(), getArgument());
		BankDepositCommand  deposit  = new BankDepositCommand(getGangland(), getArgumentTree(), getArgument());
		BankWithdrawCommand withdraw = new BankWithdrawCommand(getGangland(), getArgumentTree(), getArgument());
		BankBalanceCommand  balance  = new BankBalanceCommand(getGangland(), getArgumentTree(), getArgument());

		// add sub arguments
		List<Argument> arguments = new ArrayList<>();

		arguments.add(create);
		arguments.add(delete);
		arguments.add(deposit);
		arguments.add(withdraw);
		arguments.add(balance);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Bank");
	}

}
