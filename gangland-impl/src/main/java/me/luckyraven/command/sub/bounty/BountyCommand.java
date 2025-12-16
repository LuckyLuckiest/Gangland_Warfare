package me.luckyraven.command.sub.bounty;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BountyCommand extends CommandHandler {

	public BountyCommand(Gangland gangland) {
		super(gangland, "bounty", false);

		List<CommandInformation> list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("bounty"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();

		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		UserManager<Player> userManager = getGangland().getInitializer().getUserManager();

		if (commandSender instanceof Player player) {
			User<Player> user = userManager.getUser(player);

			String string      = MessageAddon.BOUNTY_CURRENT.toString();
			String replacement = SettingAddon.formatDouble(user.getBounty().getAmount());
			String replace     = string.replace("%bounty%", replacement);

			user.sendMessage(replace);
		} else help(commandSender, 1);
	}

	@Override
	protected void initializeArguments() {
		BountySetCommand   set   = new BountySetCommand(getGangland(), getArgumentTree(), getArgument());
		BountyClearCommand clear = new BountyClearCommand(getGangland(), getArgumentTree(), getArgument());

		List<Argument> arguments = new ArrayList<>();

		arguments.add(set);
		arguments.add(clear);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Bounty");
	}

}
