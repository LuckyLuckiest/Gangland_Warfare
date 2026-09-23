package org.luckyraven.gangland.command.sub.wanted;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;

import org.luckyraven.keystone.bean.Qualifier;

class WantedClearCommand extends SubArgument {

	private final JavaPlugin            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	public WantedClearCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                          @Qualifier("online") UserManager<Player> userManager) {
		super(gangland, "clear", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.userManager = userManager;

		clearTarget();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Wanted wanted = user.getWanted();
			wanted.setLevel(0);

			sender.sendMessage(Messages.WANTED_CLEARED.toString());
		};
	}

	private void clearTarget() {
		String notFound = Messages.PLAYER_NOT_FOUND.toString();

		Argument playerName = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			String playerStr = args[2];
			Player target    = Bukkit.getPlayer(playerStr);

			if (target == null) {
				sender.sendMessage(notFound.replace("%player%", playerStr));
				return;
			}

			User<Player> targetUser = userManager.getUser(target);

			if (targetUser == null) {
				sender.sendMessage(notFound.replace("%player%", playerStr));
				return;
			}

			targetUser.getWanted().setLevel(0);

			sender.sendMessage(
					Messages.WANTED_CLEARED_OTHER.toString().replace("%player%", target.getName()));
		}, sender -> Bukkit.getOnlinePlayers()
				.stream().map(Player::getName).toList());

		this.addSubArgument(playerName);
	}

}
