package me.luckyraven.command.sub.bank;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.type.Bank;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

class BankWithdrawCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	protected BankWithdrawCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "withdraw", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.userManager = gangland.getInitializer().getUserManager();

		this.addSubArgument(bankWithdraw());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasBank()) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK.toString());
				return;
			}

			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<amount>"));
		};
	}

	private OptionalArgument bankWithdraw() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);
			Bank         bank   = Bank.getInstance(user);

			if (!user.hasBank() || bank == null) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK.toString());
				return;
			}

			double argAmount;

			try {
				argAmount = Double.parseDouble(args[2]);
			} catch (NumberFormatException exception) {
				String string  = MessageAddon.MUST_BE_NUMBERS.toString();
				String replace = string.replace("%command%", args[2]);

				player.sendMessage(replace);
				return;
			}

			double inBank = bank.getEconomy().getBalance() - argAmount;

			BankCommand.processMoney(user, bank, bank.getEconomy().getBalance(), argAmount, inBank,
									 user.getEconomy().getBalance() + argAmount);

			String string  = MessageAddon.BANK_MONEY_WITHDRAW_PLAYER.toString();
			String replace = string.replace("%amount%", SettingAddon.formatDouble(argAmount));

			user.getUser().sendMessage(replace);

		}, sender -> List.of("<amount>"));
	}
}
