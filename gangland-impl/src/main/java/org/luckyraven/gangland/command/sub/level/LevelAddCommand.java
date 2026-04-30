package org.luckyraven.gangland.command.sub.level;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.events.user.UserLevelUpEvent;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.events.level.LevelUpEvent;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.List;

class LevelAddCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	protected LevelAddCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                          UserManager<Player> userManager) {
		super(gangland, "add", tree, parent);

		this.gangland    = gangland;
		this.tree        = tree;
		this.userManager = userManager;

		this.addSubArgument(levelAmount());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<amount>"));
		};
	}

	private OptionalArgument levelAmount() {
		OptionalArgument amount = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			User<Player> target = LevelCommand.resolveTarget(sender, args, 3, userManager);

			if (target == null) return;

			applyAdd(sender, args[2], target);
		}, sender -> List.of("<amount>"));

		amount.addSubArgument(targetPlayer());

		return amount;
	}

	private OptionalArgument targetPlayer() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			User<Player> target = LevelCommand.resolveTarget(sender, args, 3, userManager);

			if (target == null) return;

			applyAdd(sender, args[2], target);
		}, sender -> Bukkit.getOnlinePlayers()
				.stream().map(Player::getName).toList());
	}

	private void applyAdd(CommandSender sender, String rawAmount, User<Player> target) {
		int argAmount;

		try {
			argAmount = Integer.parseInt(rawAmount);
		} catch (NumberFormatException exception) {
			sender.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", rawAmount));
			return;
		}

		LevelUpEvent event = new UserLevelUpEvent(false, target, target.getLevel());
		int          added = target.getLevel().addLevels(argAmount, event);

		target.sendMessage(Messages.LEVEL_ADD.toString().replace("%level%", String.valueOf(added)));
	}

}
