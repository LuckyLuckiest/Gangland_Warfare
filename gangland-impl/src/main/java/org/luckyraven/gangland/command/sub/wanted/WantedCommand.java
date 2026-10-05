package org.luckyraven.gangland.command.sub.wanted;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.Map;

@CommandHandler
public final class WantedCommand extends Command {

	private final UserManager<Player> userManager;
	private final WantedStars         wantedStars;

	public WantedCommand(JavaPlugin gangland, @Qualifier("online") UserManager<Player> userManager,
	                      WantedStars wantedStars) {
		super(gangland, "wanted", true);

		this.userManager = userManager;
		this.wantedStars = wantedStars;

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
		WantedAddCommand wantedAdd = new WantedAddCommand(getPlugin(), getArgumentTree(), getArgument(),
		                                                  userManager, wantedStars);
		WantedRemoveCommand wantedRemove = new WantedRemoveCommand(getPlugin(), getArgumentTree(), getArgument(),
		                                                           userManager, wantedStars);
		WantedClearCommand wantedClear = new WantedClearCommand(getPlugin(), getArgumentTree(), getArgument(),
		                                                        userManager, wantedStars);

		getArgument().addSubArgument(wantedAdd);
		getArgument().addSubArgument(wantedRemove);
		getArgument().addSubArgument(wantedClear);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Wanted");
	}

}
