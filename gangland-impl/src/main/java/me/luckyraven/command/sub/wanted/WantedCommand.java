package me.luckyraven.command.sub.wanted;

import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.autowire.bean.Qualifier;
import me.luckyraven.util.command.CommandHandler;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

@CommandHandler
public final class WantedCommand extends Command {

	private final UserManager<Player> userManager;

	public WantedCommand(Gangland gangland, @Qualifier("online") UserManager<Player> userManager) {
		super(gangland, "wanted", true);

		this.userManager = userManager;

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
		Player       player = (Player) commandSender;
		User<Player> user   = userManager.getUser(player);

		if (user == null) return;

		user.sendMessage(Messages.WANTED_STATUS_HEADER.toString());
		user.sendMessage(GanglandChatUtil.color(user.getWanted().getLevelStars()));
	}

	@Override
	protected void initializeArguments() {
		WantedAddCommand wantedAdd = new WantedAddCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                  userManager);
		WantedRemoveCommand wantedRemove = new WantedRemoveCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                           userManager);
		WantedClearCommand wantedClear = new WantedClearCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                        userManager);

		getArgument().addSubArgument(wantedAdd);
		getArgument().addSubArgument(wantedRemove);
		getArgument().addSubArgument(wantedClear);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Wanted");
	}

}
