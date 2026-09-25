package org.luckyraven.gangland.command.sub.economy;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;

import org.luckyraven.keystone.bean.Qualifier;

class EconomyResetCommand extends SubArgument {

	private final JavaPlugin            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	protected EconomyResetCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                              @Qualifier("online") UserManager<Player> userManager) {
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

			applyReset(sender, target);
		};
	}

	private OptionalArgument resetTarget() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			User<Player> target = EconomyCommand.resolveTarget(sender, args, 2, userManager);

			if (target == null) return;

			applyReset(sender, target);
		}, sender -> Bukkit.getOnlinePlayers()
				.stream().map(Player::getName).toList());
	}

	static void applyReset(CommandSender sender, User<Player> target) {
		target.getEconomy().setAmount(Currency.ZERO);
		target.getUser().sendMessage(Messages.RESET_MONEY_PLAYER.toString());

		if (sender != target.getUser()) {
			sender.sendMessage(Messages.RESET_MONEY_TARGET.toString().replace("%target%", target.getUser().getName()));
		}
	}

}
