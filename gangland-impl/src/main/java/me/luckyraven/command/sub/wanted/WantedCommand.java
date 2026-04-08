package me.luckyraven.command.sub.wanted;

import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.command.CommandHandler;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

@CommandHandler
public final class WantedCommand extends Command {

	public WantedCommand(Gangland gangland) {
		super(gangland, "wanted", true);

		var list = getCommands().entrySet()
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

		if (user == null) return;

		user.sendMessage(GanglandChatUtil.commandMessage("Wanted Status:"));
		user.sendMessage(GanglandChatUtil.color(user.getWanted().getLevelStars()));
	}

	@Override
	protected void initializeArguments() {
		WantedAddCommand    wantedAdd    = new WantedAddCommand(getGangland(), getArgumentTree(), getArgument());
		WantedRemoveCommand wantedRemove = new WantedRemoveCommand(getGangland(), getArgumentTree(), getArgument());
		WantedClearCommand  wantedClear  = new WantedClearCommand(getGangland(), getArgumentTree(), getArgument());

		getArgument().addSubArgument(wantedAdd);
		getArgument().addSubArgument(wantedRemove);
		getArgument().addSubArgument(wantedClear);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Wanted");
	}

}
