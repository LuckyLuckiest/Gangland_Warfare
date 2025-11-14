package me.luckyraven.command.sub.bounty;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.feature.bounty.Bounty;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
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
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<player>"));
		};
	}

	private void bountyClear() {
		Argument playerName = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			String playerStr = args[2];
			Player player    = Bukkit.getPlayer(playerStr);

			if (player == null) {
				sender.sendMessage(MessageAddon.PLAYER_NOT_FOUND.toString().replace("%player%", playerStr));
				return;
			}

			User<Player> user = userManager.getUser(player);

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
															  .replace("%amount%", SettingAddon.formatDouble(amount))
															  .replace("%player%", playerStr));

			if (sender instanceof Player senderPlayer) {
				User<Player> userSender = userManager.getUser(senderPlayer);

				userSender.getEconomy().deposit(amount);
				senderPlayer.sendMessage(MessageAddon.DEPOSIT_MONEY_PLAYER.toString()
																		  .replace("%amount%",
																				   SettingAddon.formatDouble(amount)));
			}

			if (userBounty.getAmount() == 0D) {
				player.sendMessage(MessageAddon.BOUNTY_CLEAR.toString());
			} else {
				player.sendMessage(MessageAddon.BOUNTY_LIFTED.toString()
															 .replace("%amount%", SettingAddon.formatDouble(amount))
															 .replace("%bounty%", SettingAddon.formatDouble(
																	 userBounty.getAmount())));
			}

		}, sender -> Bukkit.getOnlinePlayers()
				.stream().map(Player::getName).toList());

		this.addSubArgument(playerName);
	}
}
