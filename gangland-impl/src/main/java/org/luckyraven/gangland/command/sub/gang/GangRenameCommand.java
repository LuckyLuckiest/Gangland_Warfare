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
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.List;

class GangRenameCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final GangManager         gangManager;

	protected GangRenameCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                            UserManager<Player> userManager, GangManager gangManager) {
		super(gangland, "rename", tree, parent);

		this.gangland    = gangland;
		this.tree        = tree;
		this.userManager = userManager;
		this.gangManager = gangManager;

		gangRename();
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

			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
		};
	}

	private void gangRename() {
		Argument changeName = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			Gang   gang    = gangManager.getGang(user.getGangId());
			String oldName = gang.getName();
			String newName = args[2];

			if (!Settings.isGangNameDuplicates()) for (Gang checkGangName : gangManager.getGangs().values())
				if (checkGangName.getName().equalsIgnoreCase(newName)) {
					user.sendMessage(Messages.DUPLICATE_GANG_NAME.toString().replace("%gang%", newName));
					return;
				}

			gang.setName(newName);

			for (User<Player> onlineMembers : gang.getOnlineMembers(userManager::getUser))
				onlineMembers.getUser()
				             .sendMessage(Messages.GANG_RENAME.toString()
				                                              .replace("%old_gang%", oldName)
				                                              .replace("%gang%", gang.getName()));
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return null;

			if (!user.hasGang()) {
				return null;
			}

			return List.of("<new-name>");
		});

		this.addSubArgument(changeName);
	}

}
