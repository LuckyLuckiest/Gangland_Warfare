package org.luckyraven.gangland.command.sub.bounty;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.wanted.BountyPayoff;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/**
 * {@code /glw bounty pay}: pay off your own bounty in full; the money goes to the city (see {@link BountyPayoff}).
 */
class BountyPayCommand extends SubArgument {

	private final UserManager<Player> userManager;

	public BountyPayCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                        @Qualifier("online") UserManager<Player> userManager) {
		super(gangland, "pay", tree, parent);

		this.userManager = userManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			// BountyCommand is user=false, so the framework lets the console through
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return;
			}

			User<Player> user = userManager.getUser(player);

			if (user == null) return;

			player.sendMessage(BountyPayoff.pay(user).text());
		};
	}

}
