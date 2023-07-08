package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.OptionalArgument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Types;
import java.util.*;
import java.util.function.Supplier;

public class SCEconomy extends CommandHandler {

	private final Gangland gangland;

	public SCEconomy(Gangland gangland) {
		super(gangland, "economy", false, "eco");
		this.gangland = gangland;

		List<CommandInformation> list = getCommands().entrySet().stream().filter(
				entry -> entry.getKey().startsWith("economy")).sorted(Map.Entry.comparingByKey()).map(
				Map.Entry::getValue).toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		help(commandSender, 1);
	}

	@Override
	protected void initializeArguments(Gangland gangland) {
		UserManager<Player>                     userManager = gangland.getInitializer().getUserManager();
		HashMap<String, Supplier<List<Player>>> specifiers  = new HashMap<>();

		// glw economy deposit
		Argument deposit = new Argument(new String[]{"deposit", "add"}, getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING, "<specifier>"));
		});


		// glw economy withdraw
		Argument withdraw = new Argument(new String[]{"withdraw", "take"}, getArgumentTree(),
		                                 (argument, sender, args) -> sender.sendMessage(
				                                 CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING,
				                                                             "<specifier>")));

		// glw economy set
		Argument set = new Argument("set", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING, "<specifier>"));
		});

		Argument amount = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			String specifier = args[2].startsWith("@") ? args[2].toLowerCase() : "@" + args[2];

			if (specifier.equalsIgnoreCase("@me") && !(sender instanceof Player)) {
				sender.sendMessage(MessageAddon.NOT_PLAYER);
				return;
			}

			try {
				double argAmount = Double.parseDouble(args[3]);
				double value     = 0D;

				List<Player> players = specifiers.get(specifier).get();

				for (Player player : players) {
					User<Player> user = userManager.getUser(player);
					switch (args[1].toLowerCase()) {
						case "deposit", "add" -> {
							value = Math.min(user.getBalance() + argAmount, SettingAddon.getPlayerMaxBalance());
							user.getUser().sendMessage(MessageAddon.PLAYER_MONEY_ADD.replace("%amount%",
							                                                                 MessageAddon.formatDouble(
									                                                                 argAmount)));
						}
						case "withdraw", "take" -> {
							value = Math.max(user.getBalance() - argAmount, 0D);
							user.getUser().sendMessage(MessageAddon.PLAYER_MONEY_TAKE.replace("%amount%",
							                                                                  MessageAddon.formatDouble(
									                                                                  argAmount)));
						}
						case "set" -> {
							value = Math.min(argAmount, SettingAddon.getPlayerMaxBalance());
							user.getUser().sendMessage(MessageAddon.PLAYER_MONEY_SET.replace("%amount%",
							                                                                 MessageAddon.formatDouble(
									                                                                 argAmount)));
						}
					}
					user.setBalance(value);
					moneyInDatabase(player, value);
				}
			} catch (NumberFormatException exception) {
				sender.sendMessage(MessageAddon.MUSTBE_NUMBER);
			}
		});

		Argument specifier = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING, "<amount>"));
		});

		specifier.setExecuteOnPass(
				(sender, args) -> collectSpecifiers(specifiers, sender, args.length > 2 ? args[2] : null));

		specifier.addSubArgument(amount);

		deposit.addSubArgument(new Argument(specifier));
		withdraw.addSubArgument(new Argument(specifier));
		set.addSubArgument(new Argument(specifier));


		// glw economy reset
		Argument reset = new Argument("reset", getArgumentTree());

		reset.setExecuteOnPass((sender, args) -> {
			collectSpecifiers(specifiers, sender, args.length > 2 ? args[2] : null);

			if (args[2].equalsIgnoreCase("@me") && !(sender instanceof Player)) {
				sender.sendMessage(MessageAddon.NOT_PLAYER);
				return;
			}

			for (List<Player> players : specifiers.values().stream().map(Supplier::get).toList())
				for (User<Player> user : players.stream().map(userManager::getUser).toList()) {
					user.setBalance(0D);
					user.getUser().sendMessage(MessageAddon.PLAYER_MONEY_RESET);
				}
		});

		Argument resetSpecifier = new OptionalArgument(getArgumentTree());

		reset.addSubArgument(resetSpecifier);

		getArgument().addSubArgument(deposit);
		getArgument().addSubArgument(withdraw);
		getArgument().addSubArgument(set);
		getArgument().addSubArgument(reset);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Economy");
	}

	private void collectSpecifiers(HashMap<String, Supplier<List<Player>>> specifiers, CommandSender sender,
	                               String target) {
		allPlayers(specifiers);
		randomPlayer(specifiers);
		nearestPlayer(specifiers, sender);
		senderSpecifier(specifiers, sender);
		if (target == null || target.isEmpty()) target = "@me";
		if (!target.startsWith("@")) target = "@" + target;
		targetSpecifier(specifiers, target);

		if (!specifiers.containsKey(target)) throw new IllegalArgumentException("Unable to identify this specifier!");
	}

	private void allPlayers(HashMap<String, Supplier<List<Player>>> specifiers) {
		specifiers.put("@a", () -> new ArrayList<>(Bukkit.getOnlinePlayers()));
	}

	private void randomPlayer(HashMap<String, Supplier<List<Player>>> specifiers) {
		specifiers.put("@r", () -> {
			List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
			if (!players.isEmpty()) {
				Player random = players.get(new Random().nextInt(players.size()));
				return Collections.singletonList(random);
			}
			return Collections.emptyList();
		});
	}

	private void nearestPlayer(HashMap<String, Supplier<List<Player>>> specifiers, CommandSender sender) {
		specifiers.put("@p", () -> {
			Player nearestPlayer   = null;
			double closestDistance = Double.MAX_VALUE;
			if (sender instanceof Player pl) for (Player player : Bukkit.getServer().getOnlinePlayers()) {
				double distance = player.getLocation().distanceSquared(pl.getLocation());
				if (distance < closestDistance) {
					closestDistance = distance;
					nearestPlayer = player;
				}
			}
			List<Player> selectedPlayers = new ArrayList<>();
			if (nearestPlayer != null) selectedPlayers.add(nearestPlayer);
			return selectedPlayers;
		});
	}

	private void senderSpecifier(HashMap<String, Supplier<List<Player>>> specifiers, CommandSender sender) {
		specifiers.put("@me", () -> {
			List<Player> players = new ArrayList<>();
			if (sender instanceof Player player) players.add(player);
			return players;
		});
	}

	private void targetSpecifier(HashMap<String, Supplier<List<Player>>> specifiers, String target) {
		List<Player> players = new ArrayList<>();
		for (Player player : Bukkit.getOnlinePlayers())
			if (player.getName().equalsIgnoreCase(target.substring(1))) {
				players.add(player);
				break;
			}

		if (!players.isEmpty()) specifiers.put(target, () -> players);
	}

	private void moneyInDatabase(Player player, double amount) {
		for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
			if (handler instanceof UserDatabase) {
				DatabaseHelper helper = new DatabaseHelper(gangland, handler);

				helper.runQueries(database -> {
					database.table("account").update("uuid = ?", new Object[]{player.getUniqueId()},
					                                 new int[]{Types.CHAR}, new String[]{"balance"},
					                                 new Object[]{amount}, new int[]{Types.DOUBLE});
				});
				break;
			}
	}

}
