package me.luckyraven.command.sub.turf;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.contract.GangLookupContract;
import me.luckyraven.turf.contract.TurfMessageContract;
import me.luckyraven.turf.data.Turf;
import me.luckyraven.turf.data.TurfRuntimeState;
import me.luckyraven.turf.listener.GangDisplayNameResolver;
import me.luckyraven.turf.manager.TurfManager;
import me.luckyraven.turf.selection.WandSelectionManager;
import org.bukkit.command.CommandSender;

/**
 * {@code /glw turf info} — prints owner / bounds / income / state for the admin's active-selection turf (or the one
 * they are standing inside if no selection is set).
 */
class TurfInfoCommand extends SubArgument {

	private final TurfManager          turfs;
	private final GangLookupContract   gangs;
	private final WandSelectionManager selections;
	private final TurfMessageContract  messages;

	protected TurfInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                          TurfManager turfs, GangLookupContract gangs, WandSelectionManager selections,
	                          TurfMessageContract messages) {
		super(gangland, "info", tree, parent);

		this.turfs      = turfs;
		this.gangs      = gangs;
		this.selections = selections;
		this.messages   = messages;
	}

	static void renderInfo(CommandSender sender, GangLookupContract gangs, TurfManager turfs,
	                       TurfMessageContract messages, Turf turf) {
		String gangName = "Unclaimed";
		if (turf.getOwnerGangId() != null) {
			Gang owner = gangs.findById(turf.getOwnerGangId());
			gangName = GangDisplayNameResolver.resolve(owner);
		}
		TurfRuntimeState state    = turfs.getRuntimeState(turf.getId());
		String           stateStr = state == null ? "IDLE" : state.getState().name();

		messages.send(sender, "TURF_INFO_HEADER",
		              "turf", turf.getDisplayName(),
		              "id", String.valueOf(turf.getId()));
		messages.send(sender, "TURF_INFO_OWNER", "gang", gangName);
		messages.send(sender, "TURF_INFO_REGION",
		              "min", turf.getRegion().getMinX() + "," + turf.getRegion().getMinZ(),
		              "max", turf.getRegion().getMaxX() + "," + turf.getRegion().getMaxZ(),
		              "world", turf.getRegion().getWorld());
		messages.send(sender, "TURF_INFO_INCOME",
		              "money_symbol", Settings.getMoneySymbol(),
		              "amount", Settings.formatAmount(turf.getIncomeAmount()),
		              "time", messages.formatDuration(Settings.getTurfIncomeIntervalMinutes() * 60L));
		messages.send(sender, "TURF_INFO_STATE", "state", stateStr);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Turf turf = TurfSelectionResolver.resolve(sender, turfs, selections, messages);
			if (turf == null) {
				return;
			}
			renderInfo(sender, gangs, turfs, messages, turf);
		};
	}
}
