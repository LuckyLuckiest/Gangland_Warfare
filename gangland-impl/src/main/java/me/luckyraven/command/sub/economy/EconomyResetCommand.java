package me.luckyraven.command.sub.economy;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.economy.bank.Currency;
import me.luckyraven.file.configuration.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class EconomyResetCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	protected EconomyResetCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                              UserManager<Player> userManager) {
		super(gangland, "reset", tree, parent);

		this.gangland    = gangland;
		this.tree        = tree;
		this.userManager = userManager;

		this.addSubArgument(resetTarget());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			User<Player> target = EconomyCommand.resolveTarget(sender, args, 2, userManager);

			if (target == null) return;

			applyReset(target);
		};
	}

	private OptionalArgument resetTarget() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			User<Player> target = EconomyCommand.resolveTarget(sender, args, 2, userManager);

			if (target == null) return;

			applyReset(target);
		}, sender -> Bukkit.getOnlinePlayers()
				.stream().map(Player::getName).toList());
	}

	private void applyReset(User<Player> target) {
		target.getEconomy().setAmount(Currency.ZERO);
		target.getUser().sendMessage(Messages.RESET_MONEY_PLAYER.toString());
	}

}
