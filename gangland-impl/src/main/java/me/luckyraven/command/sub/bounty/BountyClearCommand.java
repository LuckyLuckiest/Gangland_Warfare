package me.luckyraven.command.sub.bounty;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.bounty.Bounty;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class BountyClearCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	public BountyClearCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, new String[]{"clear", "remove", "delete", "del"}, tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.userManager = gangland.getInitializer().getUserManager();

		this.bountyClear();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<player>"));
		};
	}

	private void bountyClear() {
		Argument playerName = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			String playerStr = args[2];
			Player player    = Bukkit.getPlayer(playerStr);

			if (player == null) {
				String string  = Messages.PLAYER_NOT_FOUND.toString();
				String replace = string.replace("%player%", playerStr);
				sender.sendMessage(replace);
				return;
			}

			User<Player> user = userManager.getUser(player);

			if (user == null) return;

			if (!user.getBounty().hasBounty()) {
				sender.sendMessage(Messages.NO_BOUNTY.toString());
				return;
			}

			Bounty userBounty = user.getBounty();

			if (!userBounty.containsBounty(sender)) {
				sender.sendMessage(Messages.NO_USER_SET_BOUNTY.toString());
				return;
			}

			double amount = userBounty.getSetAmount(sender);

			// remove the user
			userBounty.removeBounty(sender);

			String string = Messages.BOUNTY_PLAYER_LIFT.toString();
			String replace = string.replace("%amount%", Settings.formatDouble(amount))
								   .replace("%player%", playerStr);

			sender.sendMessage(replace);

			if (sender instanceof Player senderPlayer) {
				User<Player> userSender = userManager.getUser(senderPlayer);

				if (userSender == null) return;

				userSender.getEconomy().deposit(amount);

				String string1  = Messages.DEPOSIT_MONEY_PLAYER.toString();
				String replace1 = string1.replace("%amount%", Settings.formatDouble(amount));

				senderPlayer.sendMessage(replace1);
			}

			if (userBounty.getAmount() == 0D) {
				user.sendMessage(Messages.BOUNTY_CLEAR.toString());
			} else {
				String string1 = Messages.BOUNTY_LIFTED.toString();
				String replace1 = string1.replace("%amount%", Settings.formatDouble(amount))
										 .replace("%bounty%", Settings.formatDouble(userBounty.getAmount()));

				user.sendMessage(replace1);
			}

		}, sender -> Bukkit.getOnlinePlayers()
				.stream().map(Player::getName).toList());

		this.addSubArgument(playerName);
	}
}
