package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.UnhandledError;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class BalanceCommand extends CommandHandler {

	private final UserManager<Player> userManager;

	public BalanceCommand(Gangland gangland) {
		super(gangland, "balance", false, "bal");

		this.userManager = gangland.getInitializer().getUserManager();

		List<CommandInformation> list = getCommands().entrySet()
													 .stream()
													 .filter(entry -> entry.getKey().startsWith("balance"))
													 .sorted(Map.Entry.comparingByKey())
													 .map(Map.Entry::getValue)
													 .toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		if (commandSender instanceof Player player) {
			// Initialize a user
			User<Player> user = getGangland().getInitializer().getUserManager().getUser(player);
			player.sendMessage(ChatUtil.color("&6" + player.getName() + "&7 balance:"));
			player.sendMessage(ChatUtil.color(
					"&a" + SettingAddon.getMoneySymbol() + SettingAddon.formatDouble(user.getEconomy().getBalance())));
		} else {
			commandSender.sendMessage(ChatUtil.informationMessage("Balance are for registered users"));
		}
	}

	@Override
	protected void initializeArguments() {
		Argument targetBalance = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			// get the target, validate if they are in the system
			String       target = args[1];
			User<Player> user   = userManager.getUser(Bukkit.getPlayer(target));

			if (user != null) {
				sender.sendMessage(MessageAddon.BALANCE_TARGET.toString()
															  .replace("%target%", target)
															  .replace("%balance%", SettingAddon.formatDouble(
																	  user.getEconomy().getBalance())));
				return;
			}

			UserDatabase userHandler = getGangland().getInitializer()
													.getDatabaseManager()
													.getDatabases()
													.stream()
													.filter(handler -> handler instanceof UserDatabase)
													.map(handler -> (UserDatabase) handler)
													.findFirst()
													.orElse(null);

			if (userHandler == null) {
				Gangland.getLog4jLogger().error(UnhandledError.ERROR + ": Unable to find UserDatabase class.");
				return;
			}

			DatabaseHelper helper = new DatabaseHelper(getGangland(), userHandler);

			helper.runQueries(database -> {
				// get all the user's data
				List<Object[]> usersData = database.table("data").selectAll();

				// get only the uuids
				Map<UUID, Double> uuids = usersData.stream()
												   .collect(Collectors.toMap(
														   objects -> UUID.fromString(String.valueOf(objects[0])),
														   objects -> (double) objects[6]));

				// iterate over all uuids and check if the name is similar to target
				boolean found = false;
				for (UUID uuid : uuids.keySet()) {
					OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
					if (offlinePlayer.getName() == null) continue;

					if (offlinePlayer.getName().equalsIgnoreCase(target)) {
						found = true;
						sender.sendMessage(MessageAddon.BALANCE_TARGET.toString()
																	  .replace("%target%", target)
																	  .replace("%balance%", SettingAddon.formatDouble(
																			  uuids.get(uuid))));

						break;
					}
				}

				if (!found) sender.sendMessage(MessageAddon.PLAYER_NOT_FOUND.toString().replace("%player%", target));
			});
		});

		getArgument().addSubArgument(targetBalance);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Balance");
	}

}
