package me.luckyraven.command.sub.bounty;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.feature.bounty.Bounty;
import me.luckyraven.feature.bounty.BountyEvent;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
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
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<player>"));
		};
	}

	private void bountySet() {
		String string = MessageAddon.PLAYER_NOT_FOUND.toString();
		Argument playerName = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			String playerStr = args[2];
			Player player    = Bukkit.getPlayer(playerStr);

			if (player == null) {
				String replace = string.replace("%player%", playerStr);
				sender.sendMessage(replace);
				return;
			}

			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<amount>"));
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
				String string1 = MessageAddon.MUST_BE_NUMBERS.toString();
				String replace = string1.replace("%command%", amountStr);

				sender.sendMessage(replace);
				return;
			}

			User<Player> user        = userManager.getUser(player);
			Bounty       userBounty  = user.getBounty();
			BountyEvent  bountyEvent = new BountyEvent(false, userBounty);

			bountyEvent.setUserBounty(user);

			if (userBounty.size() == 0) user.sendMessage(MessageAddon.BOUNTY_SET.toString());

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

					String string1 = MessageAddon.WITHDRAW_MONEY_PLAYER.toString();
					String replace = string1.replace("%amount%", SettingAddon.formatDouble(value));

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
