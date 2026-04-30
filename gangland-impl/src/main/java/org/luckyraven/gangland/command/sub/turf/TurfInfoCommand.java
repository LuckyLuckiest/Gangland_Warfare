package org.luckyraven.gangland.command.sub.turf;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.listener.GangDisplayNameResolver;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;

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
