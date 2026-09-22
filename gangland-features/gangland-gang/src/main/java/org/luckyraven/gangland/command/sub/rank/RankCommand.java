package org.luckyraven.gangland.command.sub.rank;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.gangland.command.data.CommandInformation;
import org.luckyraven.gangland.command.sub.rank.parent.RankParentCommand;
import org.luckyraven.gangland.command.sub.rank.permission.RankPermissionCommand;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.RankManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public final class RankCommand extends Command {

	private final RankManager        rankManager;
	private final RepositoryRegistry repositoryRegistry;
	private final PermissionManager  permissionManager;
	private final MemberManager      memberManager;

	public RankCommand(JavaPlugin gangland, RankManager rankManager, RepositoryRegistry repositoryRegistry,
	                   PermissionManager permissionManager, MemberManager memberManager) {
		super(gangland, "rank", false);

		this.rankManager        = rankManager;
		this.repositoryRegistry = repositoryRegistry;
		this.permissionManager  = permissionManager;
		this.memberManager      = memberManager;

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
		Argument create = new RankCreateCommand(getPlugin(), getArgumentTree(), getArgument(), rankManager);
		Argument delete = new RankDeleteCommand(getPlugin(), getArgumentTree(), getArgument(), rankManager,
		                                        repositoryRegistry);
		Argument list = new RankListCommand(getPlugin(), getArgumentTree(), getArgument(), rankManager);
		Argument permission = new RankPermissionCommand(getPlugin(), getArgumentTree(), getArgument(), rankManager,
		                                                permissionManager, memberManager);
		Argument info         = new RankInfoCommand(getPlugin(), getArgumentTree(), getArgument(), rankManager);
		Argument parent       = new RankParentCommand(getPlugin(), getArgumentTree(), getArgument(), rankManager);
		Argument traverseTree = new RankTraverseCommand(getPlugin(), getArgumentTree(), getArgument(), rankManager);
		Argument vaultGroup = new RankVaultGroupCommand(getPlugin(), getArgumentTree(), getArgument(), rankManager,
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
