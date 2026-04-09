package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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

			for (User<Player> onlineMembers : gang.getOnlineMembers(userManager))
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
