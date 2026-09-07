package org.luckyraven.gangland.copsncrooks.command.turf;

import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.extension.CommandContribution;
import org.luckyraven.gangland.copsncrooks.npc.turf.TurfPowerupManager;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.List;

/**
 * Attaches {@code /glw turf powerupnpc set|remove} under the core's {@code turf} command. Registered as a bean by
 * the module's configuration; without the cops-n-crooks module this sub-argument simply does not exist.
 */
public final class TurfPowerupNpcContribution implements CommandContribution {

	private final Gangland             gangland;
	private final TurfManager          turfs;
	private final WandSelectionManager selections;
	private final TurfMessageContract  messages;
	private final TurfPowerupManager   powerupNpcs;

	public TurfPowerupNpcContribution(Gangland gangland, TurfManager turfs, WandSelectionManager selections,
	                                  TurfMessageContract messages, TurfPowerupManager powerupNpcs) {
		this.gangland    = gangland;
		this.turfs       = turfs;
		this.selections  = selections;
		this.messages    = messages;
		this.powerupNpcs = powerupNpcs;
	}

	@Override
	public String parent() {
		return "turf";
	}

	@Override
	public List<Argument> create(Tree<Argument> tree, Argument parent) {
		return List.of(new TurfPowerupNpcCommand(gangland, tree, parent, turfs, selections, messages, powerupNpcs));
	}
}
