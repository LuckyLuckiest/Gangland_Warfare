package org.luckyraven.gangland.command.sub.economy;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public final class EconomyCommand extends Command {

	private final UserManager<Player> userManager;

	public EconomyCommand(Gangland gangland, @Qualifier("online") UserManager<Player> userManager) {
		super(gangland, "economy", false, "eco");

		this.userManager = userManager;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("economy"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	/**
	 * Resolve the effective target of an economy operation.
	 * <p>
	 * If {@code args} contains an entry at {@code targetIdx}, that token is treated as an online player name and looked
	 * up; otherwise the sender itself is returned. Emits {@code PLAYER_NOT_FOUND} or {@code NOT_PLAYER} on failure and
	 * returns {@code null} — callers should abort their action when null is returned.
	 */
	static User<Player> resolveTarget(CommandSender sender, String[] args, int targetIdx,
	                                  UserManager<Player> userManager) {
		if (args.length > targetIdx) {
			String name   = args[targetIdx];
			Player target = Bukkit.getPlayer(name);

			if (target == null) {
				sender.sendMessage(Messages.PLAYER_NOT_FOUND.toString().replace("%player%", name));
				return null;
			}

			return userManager.getUser(target);
		}

		if (!(sender instanceof Player player)) {
			sender.sendMessage(Messages.NOT_PLAYER.toString());
			return null;
		}

		return userManager.getUser(player);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		help(commandSender, 1);
	}

	@Override
	protected void initializeArguments() {
		EconomyDepositCommand deposit = new EconomyDepositCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                          userManager);
		EconomyWithdrawCommand withdraw = new EconomyWithdrawCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                             userManager);
		EconomySetCommand set = new EconomySetCommand(getGangland(), getArgumentTree(), getArgument(), userManager);
		EconomyResetCommand reset = new EconomyResetCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                    userManager);

		// add sub arguments
		List<Argument> arguments = new ArrayList<>();

		arguments.add(deposit);
		arguments.add(withdraw);
		arguments.add(set);
		arguments.add(reset);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Economy");
	}

}
