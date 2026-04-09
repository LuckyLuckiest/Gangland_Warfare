package me.luckyraven.command.sub.wanted;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.wanted.Wanted;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class WantedClearCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	public WantedClearCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                          UserManager<Player> userManager) {
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
