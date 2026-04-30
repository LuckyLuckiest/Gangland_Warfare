package org.luckyraven.gangland.command.sub.economy;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.economy.Currency;
import org.luckyraven.gangland.economy.EconomyHandler;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.math.BigDecimal;
import java.util.List;

class EconomyWithdrawCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	protected EconomyWithdrawCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                                 UserManager<Player> userManager) {
		super(gangland, new String[]{"withdraw", "take"}, tree, parent);

		this.gangland    = gangland;
		this.tree        = tree;
		this.userManager = userManager;

		this.addSubArgument(withdrawAmount());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<amount>"));
		};
	}

	private OptionalArgument withdrawAmount() {
		OptionalArgument amount = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			User<Player> target = EconomyCommand.resolveTarget(sender, args, 3, userManager);

			if (target == null) return;

			applyWithdraw(sender, args[2], target);
		}, sender -> List.of("<amount>"));

		amount.addSubArgument(targetPlayer());

		return amount;
	}

	private OptionalArgument targetPlayer() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			User<Player> target = EconomyCommand.resolveTarget(sender, args, 3, userManager);

			if (target == null) return;

			applyWithdraw(sender, args[2], target);
		}, sender -> Bukkit.getOnlinePlayers()
				.stream().map(Player::getName).toList());
	}

	private void applyWithdraw(CommandSender sender, String rawAmount, User<Player> target) {
		BigDecimal argAmount;

		try {
			argAmount = Currency.parse(rawAmount);
		} catch (NumberFormatException exception) {
			sender.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", rawAmount));
			return;
		}

		EconomyHandler economy  = target.getEconomy();
		BigDecimal     current  = economy.getAmount();
		BigDecimal     newValue = current.subtract(argAmount).max(Currency.ZERO);
		BigDecimal     taken    = current.subtract(newValue);

		economy.setAmount(newValue);

		target.getUser().sendMessage(Messages.WITHDRAW_MONEY_PLAYER.toString()
		                                                           .replace("%amount%", Settings.formatAmount(taken)));
	}

}
