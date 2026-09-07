package org.luckyraven.gangland.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.database.TableLookup;
import org.luckyraven.gangland.database.tables.player.UserTable;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.keystone.persistence.database.DatabaseHelper;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@CommandHandler
public final class BalanceCommand extends Command {

	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final GanglandDatabase           ganglandDatabase;

	public BalanceCommand(Gangland gangland,
	                      @Qualifier("online") UserManager<Player> userManager,
	                      @Qualifier("offline") UserManager<OfflinePlayer> offlineUserManager,
	                      GanglandDatabase ganglandDatabase) {
		super(gangland, "balance", false, "bal");

		this.userManager        = userManager;
		this.offlineUserManager = offlineUserManager;
		this.ganglandDatabase   = ganglandDatabase;

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
			// get the target, validate if they are in the system — the caches first, the DB only as a fallback
			String                        target = args[1];
			User<? extends OfflinePlayer> user   = findCached(target, userManager, offlineUserManager);

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
		}, sender -> cachedNames(userManager, offlineUserManager));

		getArgument().addSubArgument(targetBalance);
	}

	/**
	 * Tab-completion source for {@code /glw balance <target>}: the names already held by the online and offline
	 * {@link UserManager} caches.
	 *
	 * <p>CM-05: the supplier used to run {@code UserTable.selectAllTableQuery} plus one
	 * {@code Bukkit.getOfflinePlayer(uuid)} per registered user, synchronously on the main thread, for
	 * <em>every</em> tab keystroke — a visible freeze on any sizeable user table. The offline cache is populated
	 * from that exact table at bootstrap ({@code PlayerBootstrapService.loadOfflinePlayers}), so completing from
	 * the caches covers the same names with no query at all.
	 *
	 * @return sorted, de-duplicated names; never {@code null}.
	 */
	static List<String> cachedNames(UserManager<Player> userManager,
	                                UserManager<OfflinePlayer> offlineUserManager) {
		Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

		collectNames(userManager, names);
		collectNames(offlineUserManager, names);

		return new ArrayList<>(names);
	}

	private static void collectNames(UserManager<? extends OfflinePlayer> manager, Set<String> names) {
		if (manager == null) {
			return;
		}

		for (User<? extends OfflinePlayer> user : manager.getUsers().values()) {
			String name = nameOf(user);

			if (name == null || name.isEmpty()) {
				continue;
			}

			names.add(name);
		}
	}

	/**
	 * Resolves {@code target} against the online cache first, then the offline cache, matching on name
	 * case-insensitively. Returns {@code null} when neither cache knows the name, which is the only case that
	 * still falls through to the database scan.
	 */
	static User<? extends OfflinePlayer> findCached(String target,
	                                                UserManager<Player> userManager,
	                                                UserManager<OfflinePlayer> offlineUserManager) {
		User<Player> online = findIn(target, userManager);

		if (online != null) {
			return online;
		}

		return findIn(target, offlineUserManager);
	}

	private static <T extends OfflinePlayer> User<T> findIn(String target, UserManager<T> manager) {
		if (manager == null || target == null) {
			return null;
		}

		for (User<T> user : manager.getUsers().values()) {
			if (!target.equalsIgnoreCase(nameOf(user))) {
				continue;
			}

			return user;
		}

		return null;
	}

	/**
	 * The Bukkit-visible name of a cached user, or {@code null} when the handle is missing or has never been
	 * resolved (a uuid Bukkit has not seen yet).
	 */
	private static String nameOf(User<? extends OfflinePlayer> user) {
		if (user == null) {
			return null;
		}

		OfflinePlayer offlinePlayer = user.getUser();

		return offlinePlayer == null ? null : offlinePlayer.getName();
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Balance");
	}

}
