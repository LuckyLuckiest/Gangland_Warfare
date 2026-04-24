package me.luckyraven.command.sub.rank.permission;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gang.member.MemberManager;
import me.luckyraven.gang.rank.Rank;
import me.luckyraven.gang.rank.RankManager;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;

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
