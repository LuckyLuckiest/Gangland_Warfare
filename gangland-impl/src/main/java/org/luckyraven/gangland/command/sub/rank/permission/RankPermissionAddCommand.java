package org.luckyraven.gangland.command.sub.rank.permission;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.List;
import java.util.Set;

class RankPermissionAddCommand extends SubArgument {

	private final Gangland          gangland;
	private final Tree<Argument>    tree;
	private final RankManager       rankManager;
	private final PermissionManager permissionManager;
	private final MemberManager     memberManager;

	RankPermissionAddCommand(Gangland gangland, Tree<Argument> tree, Argument parent, RankManager rankManager,
	                         PermissionManager permissionManager, MemberManager memberManager) {
		super(gangland, "add", tree, parent);

		this.gangland          = gangland;
		this.tree              = tree;
		this.rankManager       = rankManager;
		this.permissionManager = permissionManager;
		this.memberManager     = memberManager;

		rankPermission();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<rank>"));
		};
	}

	private void rankPermission() {
		Argument rankArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Rank rank = rankManager.get(args[3]);

			if (rank == null) {
				sender.sendMessage(Messages.INVALID_RANK.toString());
				return;
			}

			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<permission>"));
		}, sender -> rankManager.getRanks().values()
				.stream().map(Rank::getName).toList());

		Argument permArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Rank rank = rankManager.get(args[3]);

			if (rank == null) {
				sender.sendMessage(Messages.INVALID_RANK.toString());
				return;
			}

			String permString = args[4];

			if (rank.contains(permString)) {
				String string  = Messages.RANK_PERMISSION_EXISTS.toString();
				String replace = string.replace("%rank%", rank.getName()).replace("%permission%", permString);

				sender.sendMessage(replace);
				return;
			}

			rankManager.addPermission(rank, permString);
			memberManager.applyRankPermissionChange(rank, permString, true);

			String string  = Messages.RANK_PERMISSION_ADD.toString();
			String replace = string.replace("%rank%", rank.getName()).replace("%permission%", permString);

			sender.sendMessage(replace);
		}, sender -> {
			Set<String> permissions = permissionManager.getPermissions();

			if (permissions.isEmpty()) return List.of("<permission>");

			return permissions.stream().toList();
		});

		rankArg.addSubArgument(permArg);
		this.addSubArgument(rankArg);
	}
}
