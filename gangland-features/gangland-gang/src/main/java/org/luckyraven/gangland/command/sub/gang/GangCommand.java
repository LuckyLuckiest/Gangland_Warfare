package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.gangland.command.extension.CommandContributions;
import org.luckyraven.gangland.command.sub.gang.ally.GangAllyCommand;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public final class GangCommand extends Command {

	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final GangManager                gangManager;
	private final MemberManager              memberManager;
	private final RankManager                rankManager;
	private final RepositoryRegistry         repositoryRegistry;
	private final PermissionManager          permissionManager;
	/**
	 * Sub-arguments runtime modules attach under {@code gang} and {@code gang.ally} (the mail module contributes
	 * invite/accept and the alliance request flow). Empty when no module is installed — the core never names them.
	 */
	private final CommandContributions       contributions;
	private final InventoryService           inventoryService;

	private final JavaPlugin gangland;

	public GangCommand(JavaPlugin gangland,
	                   @Qualifier("online") UserManager<Player> userManager,
	                   @Qualifier("offline") UserManager<OfflinePlayer> offlineUserManager,
	                   GangManager gangManager,
	                   MemberManager memberManager,
	                   RankManager rankManager,
	                   RepositoryRegistry repositoryRegistry,
	                   PermissionManager permissionManager,
	                   DependencyContainer container,
	                   InventoryService inventoryService
	) {
		super(gangland, "gang", true);

		this.gangland = gangland;

		this.userManager        = userManager;
		this.offlineUserManager = offlineUserManager;
		this.gangManager        = gangManager;
		this.memberManager      = memberManager;
		this.rankManager        = rankManager;
		this.repositoryRegistry = repositoryRegistry;
		this.permissionManager  = permissionManager;
		this.inventoryService   = inventoryService;
		this.contributions      = CommandContributions.from(container);

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("gang"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		Player       player = (Player) commandSender;
		User<Player> user   = userManager.getUser(player);

		if (user == null) return;

		if (!user.hasGang()) help(commandSender, 1);
	}

	@Override
	protected void initializeArguments() {
		Argument create = new GangCreateCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                        memberManager, gangManager, rankManager, repositoryRegistry);
		Argument delete = new GangDeleteCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                        offlineUserManager, memberManager, gangManager, rankManager,
		                                        repositoryRegistry);
		Argument removeUser = new GangKickCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                          offlineUserManager, memberManager, gangManager, rankManager);
		Argument leave = new GangLeaveCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                      memberManager, gangManager, rankManager);
		Argument promoteUser = new GangPromoteCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                              memberManager, gangManager, rankManager);
		Argument demoteUser = new GangDemoteCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                            memberManager, gangManager, rankManager);
		Argument transferOwner = new GangTransferCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                                 memberManager, gangManager, rankManager);

		Argument deposit = new GangDepositCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                          memberManager, gangManager);
		Argument withdraw = new GangWithdrawCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                            memberManager, gangManager);
		Argument balance = new GangBalanceCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                          gangManager);
		Argument members = new GangMembersCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                          gangManager);
		Argument name = new GangRenameCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                      memberManager, gangManager);
		Argument description = new GangDescriptionCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                                  memberManager, gangManager);
		Argument ally = new GangAllyCommand(getPlugin(), getArgumentTree(), getArgument(), userManager, memberManager,
		                                    gangManager, contributions);
		Argument display = new GangDisplayCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                          memberManager, gangManager);
		Argument color = new GangColorCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                      memberManager, gangManager, inventoryService);

		permissionManager.addPermission(getPermission() + ".force_rank");

		// add sub arguments
		List<Argument> arguments = new ArrayList<>();

		arguments.add(create);
		arguments.add(delete);

		// invite / accept (and anything else a module hangs under /glw gang) come from installed modules
		arguments.addAll(contributions.createFor("gang", getArgumentTree(), getArgument()));

		arguments.add(removeUser);
		arguments.add(leave);

		arguments.add(promoteUser);
		arguments.add(demoteUser);
		arguments.add(transferOwner);

		arguments.add(deposit);
		arguments.add(withdraw);
		arguments.add(balance);
		arguments.add(members);

		arguments.add(name);
		arguments.add(description);

		arguments.add(ally);

		arguments.add(display);
		arguments.add(color);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Gang");
	}

	// Note: a `gangStat`/`itemToBalance` pair of private methods that built an ad hoc gang-info GUI directly on the
	// old (now-deleted) inventory-api's InventoryHandler used to live here. Grepped for callers before touching this
	// file (CUT gate, the old inventory-api package's elimination): zero call sites anywhere in the reactor or its
	// tests —
	// `/glw gang info` is served by the YAML-driven `gang_info.yml` menu through the generic InventoryParser
	// dialect instead (see InventoryParserRoundTripTest#gangStat, which pins that YAML menu, not this method).
	// Genuinely dead code; deleted rather than ported. Flagged as a docket candidate in the CUT report.

}
