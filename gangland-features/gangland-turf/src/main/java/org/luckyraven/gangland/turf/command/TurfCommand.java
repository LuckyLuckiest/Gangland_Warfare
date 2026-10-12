package org.luckyraven.gangland.turf.command;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.GanglandApi;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.HelpInfo;
import org.luckyraven.gangland.command.data.CommandInformation;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.gangland.command.extension.CommandContributions;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.capture.TurfOwnership;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.log.TurfEventBuffer;
import org.luckyraven.gangland.turf.task.TurfIncomeDistributor;
import org.luckyraven.gangland.turf.config.TurfRules;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.listener.GangDisplayNameResolver;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.gangland.turf.powerups.PowerupRegistry;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Root of the {@code /glw turf} command tree. Wand / create / delete / setowner / list / info / show / status live as
 * sub-arguments; this class only handles the help page and the bare-{@code /glw turf} convenience — if the sender is
 * inside a turf, show its info; otherwise show the sender's gang's turfs.
 */
@CommandHandler
public final class TurfCommand extends Command {

	/** Verbs a player without {@code gangland.turf.admin} may run; every other turf verb is admin only (TF-30). */
	private static final Set<String> PLAYER_VERBS = Set.of("info", "list", "rules");
	private static final int         HELP_BREAKS  = 7;

	private final TurfManager          turfs;
	private final WandSelectionManager selections;
	private final GangLookupContract   gangs;
	private final UserLookupContract   users;
	private final UserManager<Player>  userManager;
	private final TurfMessageContract  messages;
	private final GarrisonManager      garrisons;
	private final PowerupRegistry      powerupRegistry;
	private final ActiveBuffManager    activeBuffs;
	private final TurfPowerupManager   powerupNpcs;
	private final TurfOwnership        ownership;
	private final TurfSettings         settings;
	/**
	 * Sub-arguments a runtime module attaches under {@code turf}. Empty when no module is installed — the core
	 * never names them. {@code powerupnpc} used to come through here from cops-n-crooks; group I moved the turf-NPC
	 * code into this module, so it is now added directly in {@link #initializeArguments()} instead.
	 */
	private final CommandContributions contributions;
	private final TurfMessages         text;
	private final CaptureEligibility   eligibility;
	private final TurfRules            rules;
	private final TurfEventBuffer      events;

	public TurfCommand(JavaPlugin plugin,
	                   TurfManager turfs,
	                   WandSelectionManager selections,
	                   GangLookupContract gangs,
	                   UserLookupContract users,
	                   @Qualifier("online") UserManager<Player> userManager,
	                   TurfMessageContract messages,
	                   GarrisonManager garrisons,
	                   PowerupRegistry powerupRegistry,
	                   ActiveBuffManager activeBuffs,
	                   TurfPowerupManager powerupNpcs,
	                   TurfSettings settings,
	                   TurfOwnership ownership,
	                   DependencyContainer container,
	                   TurfMessages text,
	                   CaptureEligibility eligibility,
	                   TurfRules rules,
	                   TurfEventBuffer events) {
		super(plugin, "turf", false);

		this.turfs           = turfs;
		this.selections      = selections;
		this.gangs           = gangs;
		this.users           = users;
		this.userManager     = userManager;
		this.messages        = messages;
		this.garrisons       = garrisons;
		this.powerupRegistry = powerupRegistry;
		this.activeBuffs     = activeBuffs;
		this.powerupNpcs     = powerupNpcs;
		this.settings        = settings;
		this.contributions   = CommandContributions.from(container);
		this.ownership       = ownership;
		this.text            = text;
		this.eligibility     = eligibility;
		this.rules           = rules;
		this.events          = events;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("turf"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	/**
	 * The help entries a sender may run: the root {@code turf} entry, the verbs a player may run, and every verb when the
	 * sender is a turf admin. {@code usage}'s third token is the verb.
	 */
	static List<CommandInformation> visibleEntries(List<CommandInformation> all, boolean admin) {
		List<CommandInformation> visible = new ArrayList<>();
		for (CommandInformation entry : all) {
			String[] tokens = entry.usage().trim().split("\\s+");
			String   verb   = tokens.length > 2 ? tokens[2] : null;
			if (verb == null || admin || PLAYER_VERBS.contains(verb)) {
				visible.add(entry);
			}
		}
		return visible;
	}

	/** One clickable line that opens the map ({@code Turf.Map_Link}). */
	public static void sendMapLink(CommandSender sender, TurfMessages text) {
		sender.spigot().sendMessage(new ComponentBuilder(text.text("Turf.Map_Link", "&7[map] &8- open /glw map"))
				.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + GanglandApi.SHORT_PREFIX + " map"))
				.create());
	}

	@Override
	protected void onExecute(Argument argument, CommandSender sender, String[] arguments) {
		if (!(sender instanceof Player player)) {
			help(sender, 1);
			return;
		}
		Turf at = turfs.findAt(player.getLocation());
		if (at != null) {
			selections.get(player).setActiveTurfId(at.getId());
			TurfInfoCommand.renderInfo(player, gangs, turfs, messages, settings, at, eligibility, text);
			return;
		}
		User<Player> user = users.findByPlayer(player);
		if (user == null || !user.hasGang()) {
			messages.send(sender, "TURF_NO_GANG");
			return;
		}
		renderGangTurfs(sender, user.getGangId());
	}

	@Override
	protected void initializeArguments() {
		TurfWandCommand wand = new TurfWandCommand(getPlugin(), getArgumentTree(), getArgument(), selections, settings,
		                                           text);
		TurfPos1Command pos1 = new TurfPos1Command(getPlugin(), getArgumentTree(), getArgument(), selections, text);
		TurfPos2Command pos2 = new TurfPos2Command(getPlugin(), getArgumentTree(), getArgument(), selections, text);
		TurfCreateCommand create = new TurfCreateCommand(getPlugin(), getArgumentTree(), getArgument(), turfs,
		                                                 selections, messages, settings, rules, text);
		TurfDeleteCommand delete = new TurfDeleteCommand(getPlugin(), getArgumentTree(), getArgument(),
		                                                 turfs, selections, messages, text,
		                                                 garrisons, activeBuffs, powerupNpcs, ownership);
		TurfSetOwnerCommand setOwner = new TurfSetOwnerCommand(getPlugin(), getArgumentTree(), getArgument(),
		                                                       turfs, gangs, selections, messages, text, ownership);
		TurfListCommand listSub = new TurfListCommand(getPlugin(), getArgumentTree(), getArgument(), turfs, gangs,
		                                              messages, text);
		TurfInfoCommand info = new TurfInfoCommand(getPlugin(), getArgumentTree(), getArgument(),
		                                           turfs, gangs, selections, messages, settings, eligibility, text);
		TurfRulesCommand rulesSub = new TurfRulesCommand(getPlugin(), getArgumentTree(), getArgument(), rules,
		                                                settings, text, messages);
		TurfShowCommand show = new TurfShowCommand(getPlugin(), getArgumentTree(), getArgument(), turfs, selections,
		                                           messages, settings, text);
		TurfStatusCommand status = new TurfStatusCommand(getPlugin(), getArgumentTree(), getArgument(),
		                                                 turfs, gangs, selections, messages, settings, text,
		                                                 eligibility);
		TurfSelectCommand select = new TurfSelectCommand(getPlugin(), getArgumentTree(), getArgument(), turfs,
		                                                 selections, messages, text);
		TurfRenameCommand rename = new TurfRenameCommand(getPlugin(), getArgumentTree(), getArgument(), turfs,
		                                                 selections, messages, text);
		TurfTpCommand tp = new TurfTpCommand(getPlugin(), getArgumentTree(), getArgument(), turfs, selections,
		                                     messages, text);
		TurfIncomeCommand income = new TurfIncomeCommand(getPlugin(), getArgumentTree(), getArgument(), turfs,
		                                                 selections, messages, text, settings);
		TurfGarrisonCommand garrison = new TurfGarrisonCommand(getPlugin(), getArgumentTree(), getArgument(),
		                                                       turfs, selections, messages, text, garrisons);
		TurfBuffCommand buff = new TurfBuffCommand(getPlugin(), getArgumentTree(), getArgument(),
		                                           turfs, selections, messages, text, powerupRegistry, activeBuffs);
		TurfPowerupNpcCommand powerupNpc = new TurfPowerupNpcCommand(getPlugin(), getArgumentTree(), getArgument(),
		                                                             turfs, selections, messages, text, powerupNpcs);

		List<Argument> arguments = new ArrayList<>();
		arguments.add(wand);
		arguments.add(pos1);
		arguments.add(pos2);
		arguments.add(create);
		arguments.add(delete);
		arguments.add(setOwner);
		arguments.add(listSub);
		arguments.add(info);
		arguments.add(rulesSub);
		arguments.add(show);
		arguments.add(status);
		arguments.add(select);
		arguments.add(rename);
		arguments.add(tp);
		arguments.add(income);
		arguments.add(garrison);
		arguments.add(buff);
		arguments.add(powerupNpc);

		// anything else a module hangs under /glw turf comes from installed modules (powerupnpc moved off this
		// path in group I — it is added directly above now that both it and TurfPowerupManager live in this module)
		arguments.addAll(contributions.createFor("turf", getArgumentTree(), getArgument()));

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		HelpInfo visible = new HelpInfo(visibleEntries(getHelpInfo().getList(), TurfPermissions.isAdmin(sender)),
		                                HELP_BREAKS);
		int pages = Math.max(1, visible.getMaxPages());
		visible.displayHelp(sender, Math.max(1, Math.min(page, pages)), "Turf");
		sendMapLink(sender, text);
	}

	private void renderGangTurfs(CommandSender sender, int gangId) {
		Gang       gang     = gangs.findById(gangId);
		String     gangName = GangDisplayNameResolver.resolve(gang);
		List<Turf> owned    = new ArrayList<>();
		for (Turf turf : turfs.getAll()) {
			if (turf.getOwnerGangId() != null && turf.getOwnerGangId() == gangId) {
				owned.add(turf);
			}
		}
		if (owned.isEmpty()) {
			messages.send(sender, "TURF_LIST_EMPTY");
		} else {
			messages.send(sender, "TURF_LIST_HEADER", "count", String.valueOf(owned.size()));
			var from = sender instanceof Player player ? player.getLocation() : null;
			for (Turf turf : owned) {
				TurfListCommand.sendRow(sender, text, turf, gangName, from);
			}
		}
		sendGangSummary(sender, text, rules, events, gang, gangId, owned.size(), System.currentTimeMillis());
	}

	/**
	 * The cap line ({@code owned/cap - paying}), the last three event lines and the map link. Shown even when the gang
	 * holds no turfs, so a gang that has just lost its last turf still sees what happened. A dormant gang pays nothing,
	 * so its paying count is 0.
	 */
	static void sendGangSummary(CommandSender sender, TurfMessages text, TurfRules rules, TurfEventBuffer events,
	                            Gang gang, int gangId, int ownedCount, long now) {
		int cap    = rules.cap(gang == null ? 0 : gang.getLevel().getLevelValue());
		int paying = gang != null && TurfIncomeDistributor.isActive(gang, now, rules) ? Math.min(ownedCount, cap) : 0;
		sender.sendMessage(text.text("Income.Cap_Line", "&7Turfs &f%owned%/%cap% &7- &f%paying% &7paying",
		                             "owned", ownedCount, "cap", cap, "paying", paying));
		for (TurfEventBuffer.Entry entry : events.latest(gangId, 3)) {
			sender.sendMessage(entry.line());
		}
		sendMapLink(sender, text);
	}
}
