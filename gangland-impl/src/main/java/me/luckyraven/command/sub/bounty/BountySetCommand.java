package me.luckyraven.command.sub.bounty;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.bounty.Bounty;
import me.luckyraven.copsncrooks.events.bounty.BountyEvent;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.events.user.UserBountyEvent;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

class BountySetCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	public BountySetCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, new String[]{"set", "add"}, tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.userManager = gangland.getInitializer().getUserManager();

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

			String amountStr = args[3];
			double value;
			try {
				value = Double.parseDouble(amountStr);
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

				if (userSender.getEconomy().getBalance() == 0D) {
					senderPlayer.sendMessage(Messages.CANNOT_TAKE_LESS_THAN_ZERO.toString());
					return;
				} else if (userSender.getEconomy().getBalance() < value) {
					senderPlayer.sendMessage(Messages.CANNOT_TAKE_MORE_THAN_BALANCE.toString());
					return;
				} else {
					userSender.getEconomy().withdraw(value);

					String string1 = Messages.WITHDRAW_MONEY_PLAYER.toString();
					String replace = string1.replace("%amount%", Settings.formatDouble(value));

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
