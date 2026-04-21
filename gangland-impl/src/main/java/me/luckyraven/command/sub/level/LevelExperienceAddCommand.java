package me.luckyraven.command.sub.level;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.events.level.LevelUpEvent;
import me.luckyraven.events.user.UserLevelUpEvent;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

class LevelExperienceAddCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	LevelExperienceAddCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                          UserManager<Player> userManager) {
		super(gangland, "add", tree, parent);

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

			applyAdd(sender, args[3], target);
		}, sender -> List.of("<amount>"));

		amount.addSubArgument(targetPlayer());

		return amount;
	}

	private OptionalArgument targetPlayer() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			User<Player> target = LevelCommand.resolveTarget(sender, args, 4, userManager);

			if (target == null) return;

			applyAdd(sender, args[3], target);
		}, sender -> Bukkit.getOnlinePlayers()
				.stream().map(Player::getName).toList());
	}

	private void applyAdd(CommandSender sender, String rawAmount, User<Player> target) {
		double argAmount;

		try {
			argAmount = Double.parseDouble(rawAmount);
		} catch (NumberFormatException exception) {
			sender.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", rawAmount));
			return;
		}

		LevelUpEvent event = new UserLevelUpEvent(false, target, target.getLevel());

		target.getLevel().addExperience(argAmount, event);

		target.sendMessage(Messages.LEVEL_EXP_ADD.toString().replace("%experience%", String.valueOf(argAmount)));
	}

}
