package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.List;

class GangDisplayCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final GangManager         gangManager;

	protected GangDisplayCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                             UserManager<Player> userManager, GangManager gangManager) {
		super(gangland, "display", tree, parent);

		this.gangland    = gangland;
		this.tree        = tree;
		this.userManager = userManager;
		this.gangManager = gangManager;

		gangDisplay();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
		};
	}

	private void gangDisplay() {
		Argument displayName = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			String displayNameStr = args[2];
			Gang   gang           = gangManager.getGang(user.getGangId());

			gang.setDisplayName(displayNameStr);
			user.sendMessage(Messages.GANG_DISPLAY_SET.toString().replace("%display%", displayNameStr));
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return null;

			if (!user.hasGang()) {
				return null;
			}

			return List.of("<display-name>");
		});

		// glw gang display remove
		Argument removeDisplay = new Argument(gangland, "remove", tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());

			gang.setDisplayName("");
			user.sendMessage(Messages.GANG_DISPLAY_REMOVED.toString());
		});

		this.addSubArgument(removeDisplay);
		this.addSubArgument(displayName);
	}

}
