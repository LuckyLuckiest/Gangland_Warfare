package me.luckyraven.command.sub.wanted;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public final class WantedCommand extends CommandHandler {

	public WantedCommand(Gangland gangland) {
		super(gangland, "wanted", true);

		List<CommandInformation> list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("wanted"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		UserManager<Player> userManager = getGangland().getInitializer().getUserManager();

		Player       player = (Player) commandSender;
		User<Player> user   = userManager.getUser(player);

		user.sendMessage(ChatUtil.commandMessage("Wanted Status:"));
		user.sendMessage(ChatUtil.color(user.getWanted().getLevelStars()));
	}

	@Override
	protected void initializeArguments() {
		WantedAddCommand    wantedAdd    = new WantedAddCommand(getGangland(), getArgumentTree(), getArgument());
		WantedRemoveCommand wantedRemove = new WantedRemoveCommand(getGangland(), getArgumentTree(), getArgument());

		getArgument().addSubArgument(wantedAdd);
		getArgument().addSubArgument(wantedRemove);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Wanted");
	}

}
