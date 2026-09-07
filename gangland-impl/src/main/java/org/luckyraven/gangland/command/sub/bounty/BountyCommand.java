package org.luckyraven.gangland.command.sub.bounty;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public final class BountyCommand extends Command {

	private final UserManager<Player> userManager;

	public BountyCommand(Gangland gangland, @Qualifier("online") UserManager<Player> userManager) {
		super(gangland, "bounty", false);

		this.userManager = userManager;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("bounty"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();

		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		if (commandSender instanceof Player player) {
			User<Player> user = userManager.getUser(player);

			if (user == null) return;

			String string      = Messages.BOUNTY_CURRENT.toString();
			String replacement = Settings.formatAmount(user.getBounty().getAmount());
			String replace     = string.replace("%bounty%", replacement);

			user.sendMessage(replace);
		} else help(commandSender, 1);
	}

	@Override
	protected void initializeArguments() {
		BountySetCommand   set   = new BountySetCommand(getGangland(), getArgumentTree(), getArgument(), userManager);
		BountyClearCommand clear = new BountyClearCommand(getGangland(), getArgumentTree(), getArgument(), userManager);

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
