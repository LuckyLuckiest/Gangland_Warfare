package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;

class GangBalanceCommand extends SubArgument {

	private final UserManager<Player> userManager;
	private final GangManager         gangManager;

	protected GangBalanceCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                             UserManager<Player> userManager, GangManager gangManager) {
		super(gangland, new String[]{"balance", "bal"}, tree, parent);

		this.userManager = userManager;
		this.gangManager = gangManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());
			sender.sendMessage(Messages.GANG_BALANCE.toString()
			                                        .replace("%balance%", Settings.formatAmount(
															gang.getEconomy().getAmount())));
		};
	}

}
