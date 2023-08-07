package me.luckyraven.command.sub.gang;

import me.luckyraven.account.gang.Gang;
import me.luckyraven.account.gang.GangManager;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.TriConsumer;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class GangBalanceCommand extends SubArgument {

	private final UserManager<Player> userManager;
	private final GangManager         gangManager;

	protected GangBalanceCommand(Tree<Argument> tree, Argument parent, UserManager<Player> userManager,
	                             GangManager gangManager) {
		super(new String[]{"balance", "bal"}, tree, "balance", parent);

		this.userManager = userManager;
		this.gangManager = gangManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				sender.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());
			sender.sendMessage(MessageAddon.GANG_BALANCE.toString()
			                                            .replace("%balance%",
			                                                     SettingAddon.formatDouble(gang.getBalance())));
		};
	}

}
