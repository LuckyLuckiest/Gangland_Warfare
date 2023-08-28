package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.OptionalArgument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.feature.bounty.Bounty;
import me.luckyraven.feature.bounty.BountyEvent;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class BountyCommand extends CommandHandler {

	public BountyCommand(Gangland gangland) {
		super(gangland, "bounty", false);

		List<CommandInformation> list = getCommands().entrySet().stream().filter(
				entry -> entry.getKey().startsWith("bounty")).sorted(Map.Entry.comparingByKey()).map(
				Map.Entry::getValue).toList();

		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		UserManager<Player> userManager = getGangland().getInitializer().getUserManager();

		if (commandSender instanceof Player player) {
			User<Player> user = userManager.getUser(player);

			player.sendMessage(MessageAddon.BOUNTY_CURRENT.toString()
			                                              .replace("%bounty%", SettingAddon.formatDouble(
					                                              user.getBounty().getAmount())));
		} else help(commandSender, 1);
	}

	@Override
	protected void initializeArguments(Gangland gangland) {
		UserManager<Player> userManager = gangland.getInitializer().getUserManager();

		// Add bounty to a user
		// glw bounty set <player> <amount>
		Argument add = new Argument(new String[]{"set", "add"}, getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<player>"));
		});

		// Remove your set amount of bounty to a user
		// glw bounty remove <player>
		Argument remove = new Argument(new String[]{"remove", "clear"}, getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<player>"));
		});

		Argument playerName = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			String playerStr = args[2];
			Player player    = Bukkit.getPlayer(playerStr);

			if (player == null) {
				sender.sendMessage(MessageAddon.PLAYER_NOT_FOUND.toString().replace("%player%", playerStr));
				return;
			}

			User<Player> user = userManager.getUser(player);

			switch (args[1].toLowerCase()) {
				case "set", "add" -> sender.sendMessage(
						ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<amount>"));

				case "remove", "clear" -> {
					if (!user.getBounty().hasBounty()) {
						sender.sendMessage(MessageAddon.NO_BOUNTY.toString());
						return;
					}

					Bounty userBounty = user.getBounty();

					if (!userBounty.containsBounty(sender)) {
						sender.sendMessage(MessageAddon.NO_USER_SET_BOUNTY.toString());
						return;
					}

					double amount = userBounty.getSetAmount(sender);
					// remove the user
					userBounty.removeBounty(sender);
					sender.sendMessage(MessageAddon.BOUNTY_PLAYER_LIFT.toString()
					                                                  .replace("%amount%",
					                                                           SettingAddon.formatDouble(amount))
					                                                  .replace("%player%", playerStr));

					if (sender instanceof Player senderPlayer) {
						User<Player> userSender = userManager.getUser(senderPlayer);

						userSender.getEconomy().deposit(amount);
						senderPlayer.sendMessage(MessageAddon.DEPOSIT_MONEY_PLAYER.toString()
						                                                          .replace("%amount%",
						                                                                   SettingAddon.formatDouble(
								                                                                   amount)));
					}

					if (userBounty.getAmount() == 0D) player.sendMessage(MessageAddon.BOUNTY_CLEAR.toString());
					else player.sendMessage(MessageAddon.BOUNTY_LIFTED.toString()
					                                                  .replace("%amount%",
					                                                           SettingAddon.formatDouble(amount))
					                                                  .replace("%bounty%", SettingAddon.formatDouble(
							                                                  userBounty.getAmount())));

				}
			}
		});

		Argument amount = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			String playerStr = args[2];
			Player player    = Bukkit.getPlayer(playerStr);

			if (player == null) {
				sender.sendMessage(MessageAddon.PLAYER_NOT_FOUND.toString().replace("%player%", playerStr));
				return;
			}

			String amountStr = args[3];
			double value;
			try {
				value = Double.parseDouble(amountStr);
			} catch (NumberFormatException exception) {
				sender.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString().replace("%command%", amountStr));
				return;
			}

			User<Player> user        = userManager.getUser(player);
			Bounty       userBounty  = user.getBounty();
			BountyEvent  bountyEvent = new BountyEvent(userBounty);
			bountyEvent.setUserBounty(user);

			if (userBounty.size() == 0) player.sendMessage(MessageAddon.BOUNTY_SET.toString());

			// call the event
			bountyEvent.setAmountApplied(value);

			if (sender instanceof Player senderPlayer) {
				User<Player> userSender = userManager.getUser(senderPlayer);

				if (userSender.getEconomy().getBalance() == 0D) {
					senderPlayer.sendMessage(MessageAddon.CANNOT_TAKE_LESS_THAN_ZERO.toString());
					return;
				} else if (userSender.getEconomy().getBalance() < value) {
					senderPlayer.sendMessage(MessageAddon.CANNOT_TAKE_MORE_THAN_BALANCE.toString());
					return;
				} else {
					userSender.getEconomy().withdraw(value);
					senderPlayer.sendMessage(MessageAddon.WITHDRAW_MONEY_PLAYER.toString()
					                                                           .replace("%amount%",
					                                                                    SettingAddon.formatDouble(
							                                                                    value)));
				}
			}

			gangland.getServer().getPluginManager().callEvent(bountyEvent);

			if (!bountyEvent.isCancelled()) {
				userBounty.addBounty(sender, value);

			}
		});

		playerName.addSubArgument(amount);

		add.addSubArgument(playerName);
		remove.addSubArgument(playerName);

		getArgument().addSubArgument(add);
		getArgument().addSubArgument(remove);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Bounty");
	}

}
