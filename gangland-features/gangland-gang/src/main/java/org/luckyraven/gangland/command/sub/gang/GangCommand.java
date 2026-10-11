package org.luckyraven.gangland.command.sub.gang;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.GangWorldPolicy;
import org.luckyraven.gangland.gang.combat.CombatTag;
import org.luckyraven.gangland.gang.combat.FriendlyFirePolicy;
import org.luckyraven.gangland.gang.level.GangLevelTable;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.membership.GangDisbandService;
import org.luckyraven.gangland.gang.membership.GangLeadership;
import org.luckyraven.gangland.gang.membership.GangMembershipService;
import org.luckyraven.gangland.gang.safehouse.GangSafehouseService;
import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.chat.GangChatToggles;
import org.luckyraven.gangland.gang.file.GangRules;
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
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

import java.math.BigDecimal;
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
	private final GangRules                  gangRules;
	private final GangMessages               gangMessages;
	private final GangChatToggles           chatChannel;

	// seams of the gang parity wave (0.19.0): the lanes fill their bodies, the command only passes them on
	private final GangWorldPolicy        worldPolicy;
	private final GangMembershipService  membershipService;
	private final GangDisbandService     disbandService;
	private final GangLeadership         leadership;
	private final CombatTag              combatTag;
	private final GangStatsService       statsService;
	private final GangLimits             limits;
	private final GangLevelTable         levelTable;
	private final GangSafehouseService   safehouseService;
	private final FriendlyFirePolicy     friendlyFire;

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
	                   InventoryService inventoryService,
	                   GangRules gangRules,
	                   GangMessages gangMessages,
	                   GangChatToggles chatChannel,
	                   GangWorldPolicy worldPolicy,
	                   GangMembershipService membershipService,
	                   GangDisbandService disbandService,
	                   GangLeadership leadership,
	                   CombatTag combatTag,
	                   GangStatsService statsService,
	                   GangLimits limits,
	                   GangLevelTable levelTable,
	                   GangSafehouseService safehouseService,
	                   FriendlyFirePolicy friendlyFire
	) {
		// not a players-only command: runExecute refuses the console centrally, except for list and info (T-227)
		super(gangland, "gang", false);

		this.gangland = gangland;

		this.userManager        = userManager;
		this.offlineUserManager = offlineUserManager;
		this.gangManager        = gangManager;
		this.memberManager      = memberManager;
		this.rankManager        = rankManager;
		this.repositoryRegistry = repositoryRegistry;
		this.permissionManager  = permissionManager;
		this.inventoryService   = inventoryService;
		this.gangRules          = gangRules;
		this.gangMessages       = gangMessages;
		this.chatChannel        = chatChannel;
		this.worldPolicy        = worldPolicy;
		this.membershipService  = membershipService;
		this.disbandService     = disbandService;
		this.leadership         = leadership;
		this.combatTag          = combatTag;
		this.statsService       = statsService;
		this.limits             = limits;
		this.levelTable         = levelTable;
		this.safehouseService   = safehouseService;
		this.friendlyFire       = friendlyFire;
		this.contributions      = CommandContributions.from(container);

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("gang"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	/**
	 * Central gate of {@code /glw gang}: permission, then GR-40 (gangs off on the whole server, or in the player's
	 * world), then T-227 (the console may only run {@code list} and {@code info}; every other verb is refused once
	 * here instead of each child casting the sender).
	 */
	@Override
	public void runExecute(String commandPrefix, CommandSender sender, String[] args) {
		if (!sender.hasPermission(getPermission())) {
			sender.sendMessage(Messages.COMMAND_NO_PERM.toString());
			return;
		}

		if (!worldPolicy.enabled()) {
			sender.sendMessage(gangMessages.text("Worlds.Gangs_Off", "&cGangs are turned off on this server."));
			return;
		}

		if (sender instanceof Player player) {
			if (!worldPolicy.enabledIn(player.getWorld())) {
				sender.sendMessage(gangMessages.text("Worlds.Disabled_Here", "&cGangs are disabled in this world."));
				return;
			}
		} else if (!consoleMayRun(args)) {
			sender.sendMessage(Messages.NOT_PLAYER.toString());
			return;
		}

		super.runExecute(commandPrefix, sender, args);
	}

	/** The console reaches the help page and the read-only verbs {@code list} and {@code info}. */
	private static boolean consoleMayRun(String[] args) {
		if (args.length < 2) return true;

		String verb = args[1].toLowerCase();
		return verb.equals("list") || verb.equals("info") || verb.equals("help");
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		// The gang_info.yml menu takes /glw gang before this runs; whoever it didn't open for still gets an answer.
		if (commandSender instanceof Player player && isGangless(player)) {
			sendOnboarding(player);
			return;
		}
		help(commandSender, 1);
	}

	private boolean isGangless(Player player) {
		User<Player> user = userManager.getUser(player);
		return user != null && !user.hasGang();
	}

	/** Three lines for a gangless player: what a gang is, what starting one costs, and two buttons. */
	private void sendOnboarding(Player player) {
		BigDecimal fee = GangSettings.getGangCreateFee();

		player.sendMessage(gangMessages.text("Onboarding.What",
		                                     "&6Gangs &7team up to hold turf, share a bank and fight together."));
		player.sendMessage(gangMessages.text("Onboarding.Cost",
		                                     "&7Starting a gang costs &e%money_symbol%%cost%&7. Joining one is free.",
		                                     "money_symbol", Settings.getMoneySymbol(),
		                                     "cost", Settings.formatAmount(fee)));

		String browse = gangMessages.text("Onboarding.Browse", "&a[Browse gangs]");
		String create = gangMessages.text("Onboarding.Create", "&a[Create a gang]");
		BaseComponent[] buttons = new ComponentBuilder(browse)
				.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/glw gang list"))
				.append("   ", ComponentBuilder.FormatRetention.NONE)
				.append(create, ComponentBuilder.FormatRetention.NONE)
				.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/glw gang create "))
				.create();
		player.spigot().sendMessage(buttons);
	}

	@Override
	protected void initializeArguments() {
		Argument create = new GangCreateCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                        memberManager, gangManager, rankManager, repositoryRegistry,
		                                        gangRules::getNamePattern, gangMessages, membershipService);
		Argument list = new GangListCommand(getPlugin(), getArgumentTree(), getArgument(), gangManager, userManager,
		                                    gangMessages, statsService);
		Argument info = new GangInfoCommand(getPlugin(), getArgumentTree(), getArgument(), gangManager, userManager,
		                                    gangMessages, statsService);
		Argument delete = new GangDeleteCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                        offlineUserManager, memberManager, gangManager, rankManager,
		                                        repositoryRegistry, gangMessages, disbandService);
		Argument removeUser = new GangKickCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                          offlineUserManager, memberManager, gangManager, rankManager,
		                                          membershipService);
		Argument leave = new GangLeaveCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                      memberManager, gangManager, rankManager, membershipService, combatTag);
		Argument promoteUser = new GangPromoteCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                              memberManager, gangManager, rankManager);
		Argument demoteUser = new GangDemoteCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                            memberManager, gangManager, rankManager);
		Argument transferOwner = new GangTransferCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                                 memberManager, gangManager, rankManager, leadership);

		Argument deposit = new GangDepositCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                          memberManager, gangManager);
		Argument withdraw = new GangWithdrawCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                            memberManager, gangManager);
		Argument balance = new GangBalanceCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                          gangManager);
		Argument members = new GangMembersCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                          gangManager, statsService);
		Argument name = new GangRenameCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                      memberManager, gangManager, gangRules::getNamePattern, gangMessages);
		Argument description = new GangDescriptionCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                                  memberManager, gangManager);
		Argument ally = new GangAllyCommand(getPlugin(), getArgumentTree(), getArgument(), userManager, memberManager,
		                                    gangManager, contributions, friendlyFire);
		Argument display = new GangDisplayCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                          memberManager, gangManager, gangRules, gangMessages);
		Argument color = new GangColorCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                      memberManager, gangManager, inventoryService);

		permissionManager.addPermission(GangPermissions.FORCE_RANK);

		// add sub arguments
		List<Argument> arguments = new ArrayList<>();

		arguments.add(create);
		arguments.add(list);
		arguments.add(info);
		arguments.add(new GangChatCommand(getPlugin(), getArgumentTree(), getArgument(), userManager, memberManager,
		                                  gangManager, gangMessages, chatChannel));
		arguments.add(new GangJoinCommand(getPlugin(), getArgumentTree(), getArgument(), userManager, memberManager,
		                                  gangManager, rankManager, gangMessages, membershipService));
		arguments.add(new GangRecruitingCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                        memberManager, gangManager, gangMessages));
		arguments.add(new GangBountyCommand(getPlugin(), getArgumentTree(), getArgument(), userManager, memberManager,
		                                    gangManager, gangMessages));
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

		// verbs of the gang parity wave: stubs until their lanes fill them (W5-L, W5-H, W5-F, W5-U2, W5-S)
		arguments.add(new GangUpgradeCommand(getPlugin(), getArgumentTree(), getArgument(), limits, levelTable,
		                                     gangManager, userManager, memberManager, gangMessages));
		arguments.add(new GangSafehouseCommand(getPlugin(), getArgumentTree(), getArgument(), safehouseService,
		                                       limits, worldPolicy));
		arguments.add(new GangRallyCommand(getPlugin(), getArgumentTree(), getArgument(), safehouseService));
		arguments.add(new GangPvpCommand(getPlugin(), getArgumentTree(), getArgument(), gangManager, friendlyFire));
		arguments.add(new GangNoticeCommand(getPlugin(), getArgumentTree(), getArgument(), userManager,
		                                    memberManager, gangManager, gangMessages));
		arguments.add(new GangRankingCommand(getPlugin(), getArgumentTree(), getArgument(), statsService));
		arguments.add(new GangProfileCommand(getPlugin(), getArgumentTree(), getArgument(), statsService));

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
