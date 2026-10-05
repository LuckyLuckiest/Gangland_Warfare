package org.luckyraven.gangland.command.sub.bounty;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.command.util.ParsedAmount;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.exception.EconomyException;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.core.bounty.Bounty;
import org.luckyraven.gangland.core.events.bounty.BountyEvent;
import org.luckyraven.gangland.core.events.user.UserBountyEvent;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.math.BigDecimal;
import java.util.List;

import org.luckyraven.keystone.bean.Qualifier;

class BountySetCommand extends SubArgument {

	private final JavaPlugin            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	public BountySetCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                        @Qualifier("online") UserManager<Player> userManager) {
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

			String       amountStr = args[3];
			ParsedAmount parsed    = ParsedAmount.of(amountStr);

			if (!parsed.isValid()) {
				sender.sendMessage(parsed.failureMessage(amountStr));
				return;
			}

			BigDecimal value = parsed.require();

			// A negative amount used to reach withdrawAmount(), which credited the sender and wrote a negative
			// bounty into the ledger; the minimum keeps trivial 0.01 bounties out of the ledger too.
			BigDecimal minimum = Currency.of(Settings.getBountyMinimum());

			if (minimum.signum() > 0 && value.compareTo(minimum) < 0) {
				sender.sendMessage(Messages.BOUNTY_BELOW_MINIMUM.toString()
				                                                .replace("%money_symbol%", Settings.getMoneySymbol())
				                                                .replace("%minimum%", Settings.formatAmount(minimum)));
				return;
			}

			User<Player> user = userManager.getUser(player);

			if (user == null) return;

			Bounty      userBounty  = user.getBounty();
			BountyEvent bountyEvent = new UserBountyEvent(false, user, value);

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

			if (bountyEvent.isCancelled()) {
				// the poster already paid: give it back, a cancelled post must not eat the money
				if (sender instanceof Player senderPlayer) {
					User<Player> refundee = userManager.getUser(senderPlayer);

					if (refundee != null) {
						refundee.getEconomy().depositAmount(value);
						senderPlayer.sendMessage(Messages.DEPOSIT_MONEY_PLAYER.toString()
						                                                      .replace("%amount%",
						                                                               Settings.formatAmount(value)));
					}
				}
			} else {
				// Tell the target only once the bounty is really booked, not before the sender's checks can refuse it.
				if (userBounty.size() == 0) user.sendMessage(Messages.BOUNTY_SET.toString());

				// Post exactly what the sender paid: the kill pays out the posted figure, so a level-scaled post
				// minted the difference (1000 paid on a level-100 target paid out 21000).
				userBounty.addBounty(sender, value);
			}
		}, sender -> List.of("<amount>"));

		playerName.addSubArgument(amount);
		this.addSubArgument(playerName);
	}

}
