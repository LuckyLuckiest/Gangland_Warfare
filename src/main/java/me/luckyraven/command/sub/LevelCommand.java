package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LevelCommand extends CommandHandler {

	private final Gangland gangland;

	public LevelCommand(Gangland gangland) {
		super(gangland, "level", true);

		this.gangland = gangland;
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		UserManager<Player> userManager = gangland.getInitializer().getUserManager();

		User<Player> user = userManager.getUser((Player) commandSender);
	}

	@Override
	protected void initializeArguments(Gangland gangland) {

	}

	@Override
	protected void help(CommandSender sender, int page) {

	}

}
