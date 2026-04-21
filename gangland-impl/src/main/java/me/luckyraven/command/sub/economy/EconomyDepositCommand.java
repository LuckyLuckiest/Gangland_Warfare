package me.luckyraven.command.sub.economy;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.economy.bank.Currency;
import me.luckyraven.economy.bank.EconomyHandler;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.List;

class EconomyDepositCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	protected EconomyDepositCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                                UserManager<Player> userManager) {
		super(gangland, new String[]{"deposit", "add"}, tree, parent);

		this.gangland    = gangland;
		this.tree        = tree;
		this.userManager = userManager;

		this.addSubArgument(depositAmount());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<amount>"));
		};
	}

	private OptionalArgument depositAmount() {
		OptionalArgument amount = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			User<Player> target = EconomyCommand.resolveTarget(sender, args, 3, userManager);

			if (target == null) return;

			applyDeposit(sender, args[2], target);
		}, sender -> List.of("<amount>"));

		amount.addSubArgument(targetPlayer());

		return amount;
	}

	private OptionalArgument targetPlayer() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			User<Player> target = EconomyCommand.resolveTarget(sender, args, 3, userManager);

			if (target == null) return;

			applyDeposit(sender, args[2], target);
		}, sender -> Bukkit.getOnlinePlayers()
				.stream().map(Player::getName).toList());
	}

	private void applyDeposit(CommandSender sender, String rawAmount, User<Player> target) {
		BigDecimal argAmount;

		try {
			argAmount = Currency.parse(rawAmount);
		} catch (NumberFormatException exception) {
			sender.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", rawAmount));
			return;
		}

		EconomyHandler economy   = target.getEconomy();
		BigDecimal     current   = economy.getAmount();
		BigDecimal     maxAmount = Settings.getUserMaxBalance();
		BigDecimal     projected = current.add(argAmount);
		BigDecimal     newValue  = projected.min(maxAmount);
		BigDecimal     granted   = projected.compareTo(maxAmount) <= 0 ? argAmount : newValue.subtract(current);

		economy.setAmount(newValue);

		target.getUser().sendMessage(Messages.DEPOSIT_MONEY_PLAYER.toString()
		                                                          .replace("%amount%", Settings.formatAmount(granted)));
	}

}
