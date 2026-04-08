package me.luckyraven.command.sub.wanted;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.wanted.Wanted;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class WantedClearCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	public WantedClearCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "clear", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.userManager = gangland.getInitializer().getUserManager();

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

			sender.sendMessage(GanglandChatUtil.commandMessage("Wanted level cleared."));
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

			sender.sendMessage(GanglandChatUtil.commandMessage(
					"Cleared wanted level of &b" + target.getName() + "&7."));
		}, sender -> Bukkit.getOnlinePlayers()
				.stream().map(Player::getName).toList());

		this.addSubArgument(playerName);
	}

}
