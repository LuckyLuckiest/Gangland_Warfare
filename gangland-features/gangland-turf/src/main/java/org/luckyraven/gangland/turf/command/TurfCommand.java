package org.luckyraven.gangland.turf.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.gangland.command.extension.CommandContributions;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.contract.UserLookupContract;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
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

/**
 * Root of the {@code /glw turf} command tree. Wand / create / delete / setowner / list / info / show / status live as
 * sub-arguments; this class only handles the help page and the bare-{@code /glw turf} convenience — if the sender is
 * inside a turf, show its info; otherwise show the sender's gang's turfs.
 */
@CommandHandler
public final class TurfCommand extends Command {

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
	/**
	 * Sub-arguments a runtime module attaches under {@code turf}. Empty when no module is installed — the core
	 * never names them. {@code powerupnpc} used to come through here from cops-n-crooks; group I moved the turf-NPC
	 * code into this module, so it is now added directly in {@link #initializeArguments()} instead.
	 */
	private final CommandContributions contributions;

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
	                   DependencyContainer container) {
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
		this.contributions   = CommandContributions.from(container);

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("turf"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
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
			TurfInfoCommand.renderInfo(player, gangs, turfs, messages, at);
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
		TurfWandCommand wand = new TurfWandCommand(getPlugin(), getArgumentTree(), getArgument(), selections);
		TurfPos1Command pos1 = new TurfPos1Command(getPlugin(), getArgumentTree(), getArgument(), selections);
		TurfPos2Command pos2 = new TurfPos2Command(getPlugin(), getArgumentTree(), getArgument(), selections);
		TurfCreateCommand create = new TurfCreateCommand(getPlugin(), getArgumentTree(), getArgument(), turfs,
		                                                 selections, messages);
		TurfDeleteCommand delete = new TurfDeleteCommand(getPlugin(), getArgumentTree(), getArgument(),
		                                                 turfs, selections, messages);
		TurfSetOwnerCommand setOwner = new TurfSetOwnerCommand(getPlugin(), getArgumentTree(), getArgument(),
		                                                       turfs, gangs, selections, messages);
		TurfListCommand listSub = new TurfListCommand(getPlugin(), getArgumentTree(), getArgument(), turfs, gangs,
		                                              messages);
		TurfInfoCommand info = new TurfInfoCommand(getPlugin(), getArgumentTree(), getArgument(),
		                                           turfs, gangs, selections, messages);
		TurfShowCommand show = new TurfShowCommand(getPlugin(), getArgumentTree(), getArgument(), turfs, selections,
		                                           messages);
		TurfStatusCommand status = new TurfStatusCommand(getPlugin(), getArgumentTree(), getArgument(),
		                                                 turfs, gangs, selections, messages);
		TurfSelectCommand select = new TurfSelectCommand(getPlugin(), getArgumentTree(), getArgument(), turfs,
		                                                 selections, messages);
		TurfTpCommand tp = new TurfTpCommand(getPlugin(), getArgumentTree(), getArgument(), turfs, selections,
		                                     messages);
		TurfIncomeCommand income = new TurfIncomeCommand(getPlugin(), getArgumentTree(), getArgument(), turfs,
		                                                 selections, messages);
		TurfGarrisonCommand garrison = new TurfGarrisonCommand(getPlugin(), getArgumentTree(), getArgument(),
		                                                       turfs, selections, messages, garrisons);
		TurfBuffCommand buff = new TurfBuffCommand(getPlugin(), getArgumentTree(), getArgument(),
		                                           turfs, selections, messages, powerupRegistry, activeBuffs);
		TurfPowerupNpcCommand powerupNpc = new TurfPowerupNpcCommand(getPlugin(), getArgumentTree(), getArgument(),
		                                                             turfs, selections, messages, powerupNpcs);

		List<Argument> arguments = new ArrayList<>();
		arguments.add(wand);
		arguments.add(pos1);
		arguments.add(pos2);
		arguments.add(create);
		arguments.add(delete);
		arguments.add(setOwner);
		arguments.add(listSub);
		arguments.add(info);
		arguments.add(show);
		arguments.add(status);
		arguments.add(select);
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
		getHelpInfo().displayHelp(sender, page, "Turf");
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
			return;
		}
		messages.send(sender, "TURF_LIST_HEADER", "count", String.valueOf(owned.size()));
		for (Turf turf : owned) {
			TurfListCommand.sendRow(sender, messages, turf, gangName);
		}
	}
}
