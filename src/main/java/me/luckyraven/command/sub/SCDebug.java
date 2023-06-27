package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.account.gang.Gang;
import me.luckyraven.command.Argument;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SCDebug extends CommandHandler {

	private final Gangland gangland;

	public SCDebug(Gangland gangland) {
		super(gangland, "debug", false);
		this.gangland = gangland;
	}

	@Override
	protected void onExecute(CommandSender commandSender, String[] arguments) {
		commandSender.sendMessage("Test");
	}

	@Override
	protected void initializeArguments() {
		// user data
		Argument userData = new Argument(new String[]{"user-data"}, getArgumentTree(), (sender, args) -> {
			if (sender instanceof Player player) {
				UserManager<Player> userManager = gangland.getInitializer().getUserManager();

				User<Player> user = userManager.getUser(player);

				player.sendMessage(user.getLinkedAccounts().toString());
				player.sendMessage(user.getUser().toString());
				player.sendMessage(String.valueOf(user.getBalance()));
				player.sendMessage(String.valueOf(user.getKillDeathRatio()));
				player.sendMessage(String.valueOf(user.getGangId()));

			} else {
				sender.sendMessage("Does nothing yet!");
			}
		});

		// gang data
		Argument gangData = new Argument(new String[]{"gang-data"}, getArgumentTree(), (sender, args) -> {
			for (Gang gang : gangland.getInitializer().getGangManager().getGangs())
				sender.sendMessage(gang.toString());
		});

		// add sub arguments
		getArgument().addSubArgument(userData);
		getArgument().addSubArgument(gangData);
	}

	@Override
	public void help(CommandSender sender, int page) {

	}

}
