package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.OptionalArgument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;

public class EconomyCommand extends CommandHandler {

	public EconomyCommand(Gangland gangland) {
		super(gangland, "economy", false, "eco");

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
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<specifier>"));
		}, getPermission() + ".deposit");


		// glw economy withdraw
		Argument withdraw = new Argument(new String[]{"withdraw", "take"}, getArgumentTree(),
		                                 (argument, sender, args) -> sender.sendMessage(
				                                 ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(),
				                                                       "<specifier>")), getPermission() + ".withdraw");

		// glw economy set
		Argument set = new Argument("set", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<specifier>"));
		}, getPermission() + ".set");

		Argument amount = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			String specifier = args[2].startsWith("@") ? args[2].toLowerCase() : "@" + args[2];

			if (specifier.equalsIgnoreCase("@me") && !(sender instanceof Player)) {
				sender.sendMessage(MessageAddon.NOT_PLAYER.toString());
				return;
			}

			try {
				double argAmount = Double.parseDouble(args[3]);
				double value     = 0D;
				String strValue  = "";

				List<Player> players = specifiers.get(specifier).get();

				for (Player player : players) {
					User<Player> user         = userManager.getUser(player);
					double       valueChanged = 0D;

					switch (args[1].toLowerCase()) {
						case "deposit", "add" -> {
							if (user.getEconomy().getBalance() + argAmount <= SettingAddon.getUserMaxBalance())
								valueChanged = argAmount;

							value = Math.min(user.getEconomy().getBalance() + argAmount,
							                 SettingAddon.getUserMaxBalance());
							strValue = "deposit";
						}
						case "withdraw", "take" -> {
							if (argAmount > user.getEconomy().getBalance())
								valueChanged = user.getEconomy().getBalance();
							else if (user.getEconomy().getBalance() - argAmount > 0D) valueChanged = argAmount;

							value = Math.max(user.getEconomy().getBalance() - argAmount, 0D);
							strValue = "withdraw";
						}
						case "set" -> {
							value = Math.min(argAmount, SettingAddon.getUserMaxBalance());

							if (argAmount > SettingAddon.getUserMaxBalance())
								valueChanged = SettingAddon.getUserMaxBalance();
							else valueChanged = value;

							strValue = "set";
						}
					}
					user.getUser().sendMessage(MessageAddon.valueOf(strValue.toUpperCase() + "_MONEY_PLAYER")
					                                       .toString()
					                                       .replace("%amount%",
					                                                SettingAddon.formatDouble(valueChanged)));
					user.getEconomy().setBalance(value);
				}
			} catch (NumberFormatException exception) {
				sender.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString().replace("%command%", args[3]));
			}
		});

		String[] optionalSpecifier = {"@a", "@r", "@p", "@me", "@[<name>]"};
		Argument specifier = new OptionalArgument(optionalSpecifier, getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<amount>"));
		});

		specifier.setExecuteOnPass((sender, args) -> {
			try {
				collectSpecifiers(specifiers, sender, args.length > 2 ? args[2] : null);
			} catch (IllegalArgumentException exception) {
				sender.sendMessage(ChatUtil.errorMessage(exception.getMessage()));
			}
		});

		specifier.addSubArgument(amount);

		deposit.addSubArgument(new Argument(specifier));
		withdraw.addSubArgument(new Argument(specifier));
		set.addSubArgument(new Argument(specifier));

		// glw economy reset
		Argument reset = economyReset(specifiers, userManager);

		Argument resetSpecifier = new OptionalArgument(optionalSpecifier, getArgumentTree(),
		                                               (argument, sender, args) -> reset.executeArgument(sender, args));

		reset.addSubArgument(resetSpecifier);

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

	@NotNull
	private Argument economyReset(HashMap<String, Supplier<List<Player>>> specifiers, UserManager<Player> userManager) {
		Argument reset = new Argument("reset", getArgumentTree(), (argument, sender, args) -> {
			String specifierStr = args.length > 2 ? args[2] : "@me";

			if (specifierStr.equalsIgnoreCase("@me") && !(sender instanceof Player)) {
				sender.sendMessage(MessageAddon.NOT_PLAYER.toString());
				return;
			}

			List<Player> players = specifiers.get(specifierStr).get();

			for (Player player : players) {
				User<Player> user = userManager.getUser(player);

				user.getEconomy().setBalance(0D);
				user.getUser().sendMessage(MessageAddon.RESET_MONEY_PLAYER.toString());
			}
		}, getPermission() + ".reset");

		reset.setExecuteOnPass((sender, args) -> {
			try {
				collectSpecifiers(specifiers, sender, args.length > 2 ? args[2] : null);
			} catch (IllegalArgumentException exception) {
				sender.sendMessage(ChatUtil.errorMessage(exception.getMessage()));
			}
		});
		return reset;
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

}
