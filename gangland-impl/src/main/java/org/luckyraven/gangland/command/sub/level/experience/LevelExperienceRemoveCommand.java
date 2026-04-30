package org.luckyraven.gangland.command.sub.level.experience;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.command.sub.level.LevelCommand;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.List;

class LevelExperienceRemoveCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	LevelExperienceRemoveCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                             UserManager<Player> userManager) {
		super(gangland, "remove", tree, parent);

		this.gangland    = gangland;
		this.tree        = tree;
		this.userManager = userManager;

		this.addSubArgument(experienceAmount());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<amount>"));
		};
	}

	private OptionalArgument experienceAmount() {
		OptionalArgument amount = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			User<Player> target = LevelCommand.resolveTarget(sender, args, 4, userManager);

			if (target == null) return;

			applyRemove(sender, args[3], target);
		}, sender -> List.of("<amount>"));

		amount.addSubArgument(targetPlayer());

		return amount;
	}

	private OptionalArgument targetPlayer() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			User<Player> target = LevelCommand.resolveTarget(sender, args, 4, userManager);

			if (target == null) return;

			applyRemove(sender, args[3], target);
		}, sender -> Bukkit.getOnlinePlayers()
				.stream().map(Player::getName).toList());
	}

	private void applyRemove(CommandSender sender, String rawAmount, User<Player> target) {
		double argAmount;

		try {
			argAmount = Double.parseDouble(rawAmount);
		} catch (NumberFormatException exception) {
			sender.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", rawAmount));
			return;
		}

		target.getLevel().removeExperience(argAmount);

		target.sendMessage(Messages.LEVEL_EXP_REMOVE.toString().replace("%experience%", String.valueOf(argAmount)));
	}

}
