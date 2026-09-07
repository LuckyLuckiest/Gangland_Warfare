package org.luckyraven.gangland.command.sub.rank.permission;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;

public class RankPermissionCommand extends SubArgument {

	private final Gangland          gangland;
	private final Tree<Argument>    tree;
	private final RankManager       rankManager;
	private final PermissionManager permissionManager;
	private final MemberManager     memberManager;

	public RankPermissionCommand(Gangland gangland, Tree<Argument> tree, Argument parent, RankManager rankManager,
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
