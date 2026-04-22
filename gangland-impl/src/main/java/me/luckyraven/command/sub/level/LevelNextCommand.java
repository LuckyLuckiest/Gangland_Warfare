package me.luckyraven.command.sub.level;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gang.user.Level;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class LevelNextCommand extends SubArgument {

	private final UserManager<Player> userManager;

	protected LevelNextCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                           UserManager<Player> userManager) {
		super(gangland, "next", tree, parent);

		this.userManager = userManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return;
			}

			User<Player> user = userManager.getUser(player);

			if (user == null) return;

			Level  level              = user.getLevel();
			String currentLevel       = String.valueOf(level.getLevelValue());
			String maxLevel           = String.valueOf(level.getMaxLevel());
			String experience         = String.format("%.2f", level.getExperience());
			String requiredExperience = String.format("%.2f", level.experienceCalculation(level.nextLevel()));

			user.sendMessage(Messages.LEVEL_NEXT.toString()
			                                    .replace("%level%", currentLevel)
			                                    .replace("%max_level%", maxLevel)
			                                    .replace("%experience%", experience)
			                                    .replace("%required_experience%", requiredExperience));
		};
	}

}
