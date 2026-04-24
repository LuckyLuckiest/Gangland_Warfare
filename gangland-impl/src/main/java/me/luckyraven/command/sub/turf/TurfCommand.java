package me.luckyraven.command.sub.turf;

import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.copsncrooks.npc.turf.TurfPowerupManager;
import me.luckyraven.core.bean.Qualifier;
import me.luckyraven.core.bean.command.CommandHandler;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.contract.GangLookupContract;
import me.luckyraven.gang.contract.UserLookupContract;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import me.luckyraven.turf.contract.TurfMessageContract;
import me.luckyraven.turf.data.Turf;
import me.luckyraven.turf.listener.GangDisplayNameResolver;
import me.luckyraven.turf.manager.TurfManager;
import me.luckyraven.turf.powerups.ActiveBuffManager;
import me.luckyraven.turf.powerups.GarrisonManager;
import me.luckyraven.turf.powerups.PowerupRegistry;
import me.luckyraven.turf.selection.WandSelectionManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
	private final TurfPowerupManager   powerupNpcs;
	private final GarrisonManager      garrisons;
	private final PowerupRegistry      powerupRegistry;
	private final ActiveBuffManager    activeBuffs;

	public TurfCommand(Gangland gangland,
	                   TurfManager turfs,
	                   WandSelectionManager selections,
	                   GangLookupContract gangs,
	                   UserLookupContract users,
	                   @Qualifier("online") UserManager<Player> userManager,
	                   TurfMessageContract messages,
	                   TurfPowerupManager powerupNpcs,
	                   GarrisonManager garrisons,
	                   PowerupRegistry powerupRegistry,
	                   ActiveBuffManager activeBuffs) {
		super(gangland, "turf", false);

		this.turfs           = turfs;
		this.selections      = selections;
		this.gangs           = gangs;
		this.users           = users;
		this.userManager     = userManager;
		this.messages        = messages;
		this.powerupNpcs     = powerupNpcs;
		this.garrisons       = garrisons;
		this.powerupRegistry = powerupRegistry;
		this.activeBuffs     = activeBuffs;

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
		TurfWandCommand wand = new TurfWandCommand(getGangland(), getArgumentTree(), getArgument(), selections);
		TurfPos1Command pos1 = new TurfPos1Command(getGangland(), getArgumentTree(), getArgument(), selections);
		TurfPos2Command pos2 = new TurfPos2Command(getGangland(), getArgumentTree(), getArgument(), selections);
		TurfCreateCommand create = new TurfCreateCommand(getGangland(), getArgumentTree(), getArgument(), turfs,
		                                                 selections, messages);
		TurfDeleteCommand delete = new TurfDeleteCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                 turfs, selections, messages);
		TurfSetOwnerCommand setOwner = new TurfSetOwnerCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                       turfs, gangs, selections, messages);
		TurfListCommand listSub = new TurfListCommand(getGangland(), getArgumentTree(), getArgument(), turfs, gangs,
		                                              messages);
		TurfInfoCommand info = new TurfInfoCommand(getGangland(), getArgumentTree(), getArgument(),
		                                           turfs, gangs, selections, messages);
		TurfShowCommand show = new TurfShowCommand(getGangland(), getArgumentTree(), getArgument(), turfs, selections,
		                                           messages);
		TurfStatusCommand status = new TurfStatusCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                 turfs, gangs, selections, messages);
		TurfSelectCommand select = new TurfSelectCommand(getGangland(), getArgumentTree(), getArgument(), turfs,
		                                                 selections, messages);
		TurfTpCommand tp = new TurfTpCommand(getGangland(), getArgumentTree(), getArgument(), turfs, selections,
		                                     messages);
		TurfIncomeCommand income = new TurfIncomeCommand(getGangland(), getArgumentTree(), getArgument(), turfs,
		                                                 selections, messages);
		TurfPowerupNpcCommand powerupNpc = new TurfPowerupNpcCommand(getGangland(), getArgumentTree(),
		                                                             getArgument(), turfs, selections, messages,
		                                                             powerupNpcs);
		TurfGarrisonCommand garrison = new TurfGarrisonCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                       turfs, selections, messages, garrisons);
		TurfBuffCommand buff = new TurfBuffCommand(getGangland(), getArgumentTree(), getArgument(),
		                                           turfs, selections, messages, powerupRegistry, activeBuffs);

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
		arguments.add(powerupNpc);
		arguments.add(garrison);
		arguments.add(buff);
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
