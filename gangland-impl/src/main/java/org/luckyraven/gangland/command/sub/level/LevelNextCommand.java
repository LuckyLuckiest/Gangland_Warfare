package org.luckyraven.gangland.command.sub.level;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.user.Level;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;

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
