package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.user.User;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public final class BalanceCommand extends CommandHandler {

	public BalanceCommand(Gangland gangland) {
		super(gangland, "balance", true, "bal");

		List<CommandInformation> list = getCommands().entrySet().stream().filter(
				entry -> entry.getKey().startsWith("balance")).sorted(Map.Entry.comparingByKey()).map(
				Map.Entry::getValue).toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		Player player = (Player) commandSender;
		// Initialize a user
		User<Player> user = getGangland().getInitializer().getUserManager().getUser(player);
		player.sendMessage(ChatUtil.color("&6" + player.getName() + "&7 balance:"));
		player.sendMessage(
				ChatUtil.color("&a" + SettingAddon.getMoneySymbol() + SettingAddon.formatDouble(user.getBalance())));
	}

	@Override
	protected void initializeArguments(Gangland gangland) {

	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Balance");
	}

}
