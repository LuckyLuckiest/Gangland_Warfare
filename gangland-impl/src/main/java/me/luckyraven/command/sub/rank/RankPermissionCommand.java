package me.luckyraven.command.sub.rank;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.data.account.gang.member.MemberManager;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

class RankPermissionCommand extends SubArgument {

	private final Gangland          gangland;
	private final Tree<Argument>    tree;
	private final RankManager       rankManager;
	private final PermissionManager permissionManager;
	private final MemberManager     memberManager;

	protected RankPermissionCommand(Gangland gangland, Tree<Argument> tree, Argument parent, RankManager rankManager,
	                                PermissionManager permissionManager, MemberManager memberManager) {
		super(gangland, new String[]{"permission", "perm"}, tree, parent);

		this.gangland          = gangland;
		this.tree              = tree;
		this.rankManager       = rankManager;
		this.permissionManager = permissionManager;
		this.memberManager     = memberManager;

		initializeArguments();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<add/remove>"));
		};
	}

	private void initializeArguments() {
		Argument addArg = new RankPermissionAddCommand(gangland, tree, this, rankManager, permissionManager,
		                                               memberManager);
		Argument removeArg = new RankPermissionRemoveCommand(gangland, tree, this, rankManager, permissionManager,
		                                                     memberManager);

		List<Argument> arguments = new ArrayList<>();
		arguments.add(addArg);
		arguments.add(removeArg);

		this.addAllSubArguments(arguments);
	}
}
