package org.luckyraven.gangland.command.sub.bounty;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.exception.EconomyException;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.bounty.Bounty;
import org.luckyraven.gangland.gang.events.bounty.BountyEvent;
import org.luckyraven.gangland.gang.events.user.UserBountyEvent;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.math.BigDecimal;
import java.util.List;

class BountySetCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	public BountySetCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                        UserManager<Player> userManager) {
		super(gangland, new String[]{"set", "add"}, tree, parent);

		this.gangland    = gangland;
		this.tree        = tree;
		this.userManager = userManager;

		bountySet();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<player>"));
		};
	}

	private void bountySet() {
		String string = Messages.PLAYER_NOT_FOUND.toString();
		Argument playerName = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			String playerStr = args[2];
			Player player    = Bukkit.getPlayer(playerStr);

			if (player == null) {
				String replace = string.replace("%player%", playerStr);
				sender.sendMessage(replace);
				return;
			}

			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<amount>"));
		}, sender -> Bukkit.getOnlinePlayers()
				.stream().map(Player::getName).toList());

		Argument amount = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			String playerStr = args[2];
			Player player    = Bukkit.getPlayer(playerStr);

			if (player == null) {
				String replace = string.replace("%player%", playerStr);
				sender.sendMessage(replace);
				return;
			}

			String     amountStr = args[3];
			BigDecimal value;
			try {
				value = Currency.parse(amountStr);
			} catch (NumberFormatException exception) {
				String string1 = Messages.MUST_BE_NUMBERS.toString();
				String replace = string1.replace("%command%", amountStr);

				sender.sendMessage(replace);
				return;
			}

			User<Player> user = userManager.getUser(player);

			if (user == null) return;

			Bounty      userBounty  = user.getBounty();
			BountyEvent bountyEvent = new UserBountyEvent(false, user, value);

			if (userBounty.size() == 0) user.sendMessage(Messages.BOUNTY_SET.toString());

			// call the event
			if (sender instanceof Player senderPlayer) {
				User<Player> userSender = userManager.getUser(senderPlayer);

				if (userSender == null) return;

				BigDecimal senderBalance = userSender.getEconomy().getAmount();
				if (senderBalance.signum() == 0) {
					senderPlayer.sendMessage(Messages.CANNOT_TAKE_LESS_THAN_ZERO.toString());
					return;
				} else if (senderBalance.compareTo(value) < 0) {
					senderPlayer.sendMessage(Messages.CANNOT_TAKE_MORE_THAN_BALANCE.toString());
					return;
				} else {
					try {
						userSender.getEconomy().withdrawAmount(value);
					} catch (EconomyException ignored) {
						senderPlayer.sendMessage(Messages.CANNOT_TAKE_MORE_THAN_BALANCE.toString());
						return;
					}

					String string1 = Messages.WITHDRAW_MONEY_PLAYER.toString();
					String replace = string1.replace("%amount%", Settings.formatAmount(value));

					senderPlayer.sendMessage(replace);
				}
			}

			Bukkit.getPluginManager().callEvent(bountyEvent);

			if (!bountyEvent.isCancelled()) {
				userBounty.addBounty(sender, value, user.getLevel().getLevelValue());
			}
		}, sender -> List.of("<amount>"));

		playerName.addSubArgument(amount);
		this.addSubArgument(playerName);
	}

}
