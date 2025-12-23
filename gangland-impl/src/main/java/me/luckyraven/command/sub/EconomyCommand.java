package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class EconomyCommand extends CommandHandler {

	public EconomyCommand(Gangland gangland) {
		super(gangland, "economy", false, "eco");

		var list = getCommands().entrySet()
								.stream()
								.filter(entry -> entry.getKey().startsWith("economy"))
								.sorted(Map.Entry.comparingByKey())
								.map(Map.Entry::getValue)
								.toList();

		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		help(commandSender, 1);
	}

	@Override
	protected void initializeArguments() {
		UserManager<Player>                     userManager = getGangland().getInitializer().getUserManager();
		HashMap<String, Supplier<List<Player>>> specifiers  = new HashMap<>();

		// glw economy deposit
		Argument deposit = new Argument(getGangland(), new String[]{"deposit", "add"}, getArgumentTree(),
										(argument, sender, args) -> {
											sender.sendMessage(
													ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(),
																		  "<specifier>"));
										}, getPermission() + ".deposit");


		// glw economy withdraw
		Argument withdraw = new Argument(getGangland(), new String[]{"withdraw", "take"}, getArgumentTree(),
										 (argument, sender, args) -> sender.sendMessage(
												 ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(),
																	   "<specifier>")), getPermission() + ".withdraw");

		// glw economy set
		Argument set = new Argument(getGangland(), "set", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<specifier>"));
		}, getPermission() + ".set");

		Argument amount = new OptionalArgument(getGangland(), getArgumentTree(), (argument, sender, args) -> {
			String specifier;
			if (args[2].startsWith("@")) specifier = args[2];
			else if (args[2].contains("*")) specifier = args[2];
			else specifier = "@" + args[2];

			if (specifier.equals("**") && !(sender instanceof Player)) {
				sender.sendMessage(MessageAddon.NOT_PLAYER.toString());
				return;
			}

			operations(sender, args, specifiers, specifier, userManager);
		}, sender -> List.of("*", "**"));

		String[] optionalSpecifier = {"*", "**", "@[<name>]"};
		Argument specifier         = getArgument(optionalSpecifier, specifiers);

		specifier.addSubArgument(amount);

		deposit.addSubArgument(new Argument(specifier));
		withdraw.addSubArgument(new Argument(specifier));
		set.addSubArgument(new Argument(specifier));

		// glw economy reset
		Argument reset = economyReset(specifiers, userManager);

		Argument resetSpecifier = new OptionalArgument(getGangland(), optionalSpecifier, getArgumentTree(),
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

	private void operations(CommandSender sender, String[] args, HashMap<String, Supplier<List<Player>>> specifiers,
							String specifier, UserManager<Player> userManager) {
		try {
			double argAmount = Double.parseDouble(args[3]);
			double value     = 0D;
			String strValue  = "";

			List<Player> players = specifiers.get(specifier).get();

			if (players == null || players.isEmpty()) return;

			for (Player player : players) {
				User<Player> user         = userManager.getUser(player);
				double       valueChanged = 0D;

				switch (args[1].toLowerCase()) {
					case "deposit", "add" -> {
						if (user.getEconomy().getBalance() + argAmount <= SettingAddon.getUserMaxBalance()) valueChanged
								= argAmount;

						value    = Math.min(user.getEconomy().getBalance() + argAmount,
											SettingAddon.getUserMaxBalance());
						strValue = "deposit";
					}
					case "withdraw", "take" -> {
						if (argAmount > user.getEconomy().getBalance()) valueChanged = user.getEconomy().getBalance();
						else if (user.getEconomy().getBalance() - argAmount > 0D) valueChanged = argAmount;

						value    = Math.max(user.getEconomy().getBalance() - argAmount, 0D);
						strValue = "withdraw";
					}
					case "set" -> {
						value = Math.min(argAmount, SettingAddon.getUserMaxBalance());

						if (argAmount > SettingAddon.getUserMaxBalance()) valueChanged
								= SettingAddon.getUserMaxBalance();
						else valueChanged = value;

						strValue = "set";
					}
				}
				user.getUser()
					.sendMessage(MessageAddon.valueOf(strValue.toUpperCase() + "_MONEY_PLAYER")
											 .toString()
											 .replace("%amount%", SettingAddon.formatDouble(valueChanged)));
				user.getEconomy().setBalance(value);
			}
		} catch (NumberFormatException exception) {
			sender.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString().replace("%command%", args[3]));
		}
	}

	private @NotNull Argument getArgument(String[] optionalSpecifier,
										  HashMap<String, Supplier<List<Player>>> specifiers) {
		Argument specifier = new OptionalArgument(getGangland(), optionalSpecifier, getArgumentTree(),
												  (argument, sender, args) -> {
													  sender.sendMessage(ChatUtil.setArguments(
															  MessageAddon.ARGUMENTS_MISSING.toString(), "<amount>"));
												  }, sender -> {
			List<String> list = new ArrayList<>();

			list.add("*");
			list.add("**");

			list.addAll(Bukkit.getOnlinePlayers()
							  .stream().map(player -> "@" + player.getName()).toList());

			return list;
		});

		specifier.setExecuteOnPass((sender, args) -> {
			try {
				collectSpecifiers(specifiers, sender, args.length > 2 ? args[2] : null);
			} catch (IllegalArgumentException exception) {
				sender.sendMessage(ChatUtil.errorMessage(exception.getMessage()));
			}
		});
		return specifier;
	}

	@NotNull
	private Argument economyReset(HashMap<String, Supplier<List<Player>>> specifiers, UserManager<Player> userManager) {
		Argument reset = new Argument(getGangland(), "reset", getArgumentTree(), (argument, sender, args) -> {
			String specifierStr = args.length > 2 ? args[2] : "**";

			if (specifierStr.equals("**") && !(sender instanceof Player)) {
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
		senderSpecifier(specifiers, sender);

		if (target == null || target.isEmpty()) target = "**";
		if (!target.startsWith("@") && !target.equals("**")) target = "@" + target;

		targetSpecifier(specifiers, target);

		if (!specifiers.containsKey(target)) throw new IllegalArgumentException("Unable to identify this specifier!");
	}

	private void allPlayers(HashMap<String, Supplier<List<Player>>> specifiers) {
		List<Player> players = Bukkit.getOnlinePlayers()
									 .stream().map(player -> (Player) player).toList();

		specifiers.put("*", () -> new ArrayList<>(players));
	}

	private void senderSpecifier(HashMap<String, Supplier<List<Player>>> specifiers, CommandSender sender) {
		List<Player> players = Stream.of(sender).filter(s -> s instanceof Player).map(s -> (Player) s).toList();

		specifiers.put("**", () -> new ArrayList<>(players));
	}

	private void targetSpecifier(HashMap<String, Supplier<List<Player>>> specifiers, String target) {
		List<Player> players = Bukkit.getOnlinePlayers()
									 .stream()
									 .filter(player -> player.getName().equalsIgnoreCase(target.substring(1)))
									 .map(player -> (Player) player)
									 .findFirst()
									 .stream()
									 .toList();

		specifiers.put(target, () -> new ArrayList<>(players));
	}

}
