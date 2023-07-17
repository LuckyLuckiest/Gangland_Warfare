package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.account.Account;
import me.luckyraven.account.gang.Gang;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.rank.Rank;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SCDebug extends CommandHandler {

	public SCDebug(Gangland gangland) {
		super(gangland, "debug", false);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		commandSender.sendMessage("Test");
	}

	@Override
	protected void initializeArguments(Gangland gangland) {
		// user data
		Argument userData = new Argument("user-data", getArgumentTree(), (argument, sender, args) -> {
			if (sender instanceof Player player) {
				UserManager<Player> userManager = gangland.getInitializer().getUserManager();

				User<Player> user = userManager.getUser(player);

				for (Account<?, ?> account : user.getLinkedAccounts())
					sender.sendMessage(account.toString());

				player.sendMessage(user.toString());
			} else {
				sender.sendMessage("Does nothing yet!");
			}
		});

		// gang data
		Argument gangData = new Argument("gang-data", getArgumentTree(), (argument, sender, args) -> {
			for (Gang gang : gangland.getInitializer().getGangManager().getGangs().values())
				sender.sendMessage(gang.toString());
		});

		// rank data
		Argument rankData = new Argument("rank-data", getArgumentTree(), (argument, sender, args) -> {
			for (Rank rank : gangland.getInitializer().getRankManager().getRanks().values())
				sender.sendMessage(rank.toString());
		});

		// add sub arguments
		List<Argument> arguments = new ArrayList<>();

		arguments.add(userData);
		arguments.add(gangData);
		arguments.add(rankData);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	public void help(CommandSender sender, int page) {

	}

}
