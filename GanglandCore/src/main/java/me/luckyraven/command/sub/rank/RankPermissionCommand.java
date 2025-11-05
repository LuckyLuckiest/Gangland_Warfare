package me.luckyraven.command.sub.rank;

import me.luckyraven.Gangland;
import me.luckyraven.TriConsumer;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.rank.Permission;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;

class RankPermissionCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final RankManager    rankManager;

	protected RankPermissionCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, new String[]{"permission", "perm"}, tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.rankManager = gangland.getInitializer().getRankManager();

		rankPermission();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<add/remove>"));
		};
	}

	private void rankPermission() {
		Argument perm = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			// check if rank exists
			Rank rank = rankManager.get(args[3]);

			if (rank == null) {
				sender.sendMessage(MessageAddon.INVALID_RANK.toString());
				return;
			}

			// get the list
			String permString = args[4];
			String message    = "";
			switch (args[2].toLowerCase()) {
				case "add" -> {
					Permission newPermission = new Permission(Permission.getNewId(), permString);

					rank.addPermission(newPermission);

					message = MessageAddon.RANK_PERMISSION_ADD.toString()
															  .replace("%rank%", rank.getName())
															  .replace("%permission%", permString);
				}
				case "remove" -> {
					if (!rank.contains(permString)) {
						sender.sendMessage(MessageAddon.INVALID_RANK_PERMISSION.toString());
						return;
					}

					rank.removePermission(permString);

					message = MessageAddon.RANK_PERMISSION_REMOVE.toString()
																 .replace("%rank%", rank.getName())
																 .replace("%permission%", permString);
				}
			}

			sender.sendMessage(message);
		});

		Argument permName = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<permission>"));
		});

		permName.addSubArgument(perm);

		Argument addPerm = new Argument(gangland, "add", tree, (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, this.getPermission() + ".add");

		Argument removePerm = new Argument(gangland, "remove", tree, (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, this.getPermission() + ".remove");

		addPerm.addSubArgument(permName);
		removePerm.addSubArgument(permName);

		this.addSubArgument(addPerm);
		this.addSubArgument(removePerm);
	}
}
