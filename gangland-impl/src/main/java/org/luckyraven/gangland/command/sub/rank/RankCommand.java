package org.luckyraven.gangland.command.sub.rank;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.data.CommandInformation;
import org.luckyraven.gangland.command.sub.rank.parent.RankParentCommand;
import org.luckyraven.gangland.command.sub.rank.permission.RankPermissionCommand;
import org.luckyraven.gangland.core.bean.command.CommandHandler;
import org.luckyraven.gangland.data.permission.PermissionManager;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.RankManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public final class RankCommand extends Command {

	private final RankManager       rankManager;
	private final GanglandDatabase  ganglandDatabase;
	private final PermissionManager permissionManager;
	private final MemberManager     memberManager;

	public RankCommand(Gangland gangland, RankManager rankManager, GanglandDatabase ganglandDatabase,
	                   PermissionManager permissionManager, MemberManager memberManager) {
		super(gangland, "rank", false);

		this.rankManager       = rankManager;
		this.ganglandDatabase  = ganglandDatabase;
		this.permissionManager = permissionManager;
		this.memberManager     = memberManager;

		List<CommandInformation> list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("rank"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		help(commandSender, 1);
	}

	@Override
	protected void initializeArguments() {
		Argument create = new RankCreateCommand(getGangland(), getArgumentTree(), getArgument(), rankManager);
		Argument delete = new RankDeleteCommand(getGangland(), getArgumentTree(), getArgument(), rankManager,
		                                        ganglandDatabase);
		Argument list = new RankListCommand(getGangland(), getArgumentTree(), getArgument(), rankManager);
		Argument permission = new RankPermissionCommand(getGangland(), getArgumentTree(), getArgument(), rankManager,
		                                                permissionManager, memberManager);
		Argument info         = new RankInfoCommand(getGangland(), getArgumentTree(), getArgument(), rankManager);
		Argument parent       = new RankParentCommand(getGangland(), getArgumentTree(), getArgument(), rankManager);
		Argument traverseTree = new RankTraverseCommand(getGangland(), getArgumentTree(), getArgument(), rankManager);
		Argument vaultGroup = new RankVaultGroupCommand(getGangland(), getArgumentTree(), getArgument(), rankManager,
		                                                memberManager);

		// add sub arguments
		List<Argument> arguments = new ArrayList<>();

		arguments.add(create);
		arguments.add(delete);
		arguments.add(list);
		arguments.add(permission);
		arguments.add(info);
		arguments.add(parent);
		arguments.add(traverseTree);
		arguments.add(vaultGroup);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Rank");
	}

}
