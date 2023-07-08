package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.data.user.User;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SCBalance extends CommandHandler {

	private final Gangland gangland;

	public SCBalance(Gangland gangland) {
		super(gangland, "balance", true, "bal");
		this.gangland = gangland;
		getHelpInfo().add(getCommandInformation("balance"));
		getHelpInfo().add(getCommandInformation("balance_others"));
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		Player player = (Player) commandSender;
		// Initialize a user
		User<Player> user = gangland.getInitializer().getUserManager().getUser(player);
		player.sendMessage(ChatUtil.color("&6" + player.getName() + "&7 balance:"));
		player.sendMessage(ChatUtil.color("&a$" + MessageAddon.formatDouble(user.getBalance())));
	}

	@Override
	protected void initializeArguments(Gangland gangland) {

	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Balance");
	}

}
