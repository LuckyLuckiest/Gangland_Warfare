package org.luckyraven.gangland.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.core.bean.Qualifier;
import org.luckyraven.gangland.core.bean.command.CommandHandler;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.database.TableLookup;
import org.luckyraven.gangland.database.tables.player.UserTable;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.persistence.database.DatabaseHelper;
import org.luckyraven.gangland.persistence.database.component.Table;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@CommandHandler
public final class BalanceCommand extends Command {

	private final UserManager<Player> userManager;
	private final GanglandDatabase    ganglandDatabase;

	public BalanceCommand(Gangland gangland,
	                      @Qualifier("online") UserManager<Player> userManager,
	                      GanglandDatabase ganglandDatabase) {
		super(gangland, "balance", false, "bal");

		this.userManager      = userManager;
		this.ganglandDatabase = ganglandDatabase;

		var list = getCommands().entrySet()
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
			User<Player> user = userManager.getUser(player);

			if (user == null) return;

			user.sendMessage(GanglandChatUtil.color("&6" + player.getName() + "&7 balance:"));
			user.sendMessage(GanglandChatUtil.color(
					"&a" + Settings.getMoneySymbol() + Settings.formatAmount(user.getEconomy().getAmount())));
		} else {
			commandSender.sendMessage(Messages.BALANCE_REGISTERED_ONLY.toString());
		}
	}

	@Override
	protected void initializeArguments() {
		Argument targetBalance = new OptionalArgument(getGangland(), getArgumentTree(), (argument, sender, args) -> {
			// get the target, validate if they are in the system
			String       target = args[1];
			User<Player> user   = userManager.getUser(Bukkit.getPlayer(target));

			if (user != null) {
				sender.sendMessage(Messages.BALANCE_TARGET.toString()
				                                          .replace("%target%", target)
				                                          .replace("%balance%", Settings.formatAmount(
																  user.getEconomy().getAmount())));
				return;
			}

			DatabaseHelper helper = new DatabaseHelper(getGangland(), ganglandDatabase);
			List<Table<?>> tables = ganglandDatabase.getTables();

			UserTable userTable = TableLookup.find(UserTable.class, tables);

			helper.runQueries(database -> {
				// get all the user's data
				List<Object[]> usersData = userTable.selectAllTableQuery(database);

				// get only the uuids
				Map<UUID, Double> uuids = usersData.stream()
						.collect(Collectors.toMap(objects -> UUID.fromString(String.valueOf(objects[0])),
						                          objects -> (double) objects[1]));

				// iterate over all uuids and check if the name is similar to target
				boolean found = false;

				for (UUID uuid : uuids.keySet()) {
					OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
					String        offlineName   = offlinePlayer.getName();

					if (offlineName == null || offlineName.isEmpty() || !offlineName.equalsIgnoreCase(target)) continue;

					found = true;

					sender.sendMessage(Messages.BALANCE_TARGET.toString()
					                                          .replace("%target%", target)
					                                          .replace("%balance%",
					                                                   Settings.formatDouble(uuids.get(uuid))));

					break;
				}

				if (!found) sender.sendMessage(Messages.PLAYER_NOT_FOUND.toString().replace("%player%", target));
			});
		}, sender -> {
			List<String> players = new ArrayList<>();

			DatabaseHelper helper = new DatabaseHelper(getGangland(), ganglandDatabase);
			List<Table<?>> tables = ganglandDatabase.getTables();

			UserTable userTable = TableLookup.find(UserTable.class, tables);

			helper.runQueries(database -> {
				// get all the user's data
				List<Object[]> usersData = userTable.selectAllTableQuery(database);

				// get only the uuids
				Map<UUID, Double> uuids = usersData.stream()
						.collect(Collectors.toMap(objects -> UUID.fromString(String.valueOf(objects[0])),
						                          objects -> (double) objects[1]));

				for (UUID uuid : uuids.keySet()) {
					OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
					String        offlineName   = offlinePlayer.getName();

					if (offlineName == null || offlineName.isEmpty()) continue;

					players.add(offlineName);
				}

			});

			return players;
		});

		getArgument().addSubArgument(targetBalance);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Balance");
	}

}
