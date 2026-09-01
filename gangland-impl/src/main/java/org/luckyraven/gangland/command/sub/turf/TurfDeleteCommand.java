package org.luckyraven.gangland.command.sub.turf;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;

/**
 * {@code /glw turf delete} — removes the admin's active-selection turf (or the one they are standing inside if no
 * selection is set). Clears the active selection afterwards since it points at a now-deleted turf.
 */
class TurfDeleteCommand extends SubArgument {

	private final TurfManager          turfs;
	private final WandSelectionManager selections;
	private final TurfMessageContract  messages;

	protected TurfDeleteCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                            TurfManager turfs, WandSelectionManager selections, TurfMessageContract messages) {
		super(gangland, "delete", tree, parent);

		this.turfs      = turfs;
		this.selections = selections;
		this.messages   = messages;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Turf turf = TurfSelectionResolver.resolve(sender, turfs, selections, messages);
			if (turf == null) {
				return;
			}
			turfs.delete(turf);
			if (sender instanceof Player player) {
				selections.get(player).setActiveTurfId(null);
			}
			messages.send(sender, "TURF_DELETE_SUCCESS", "turf", turf.getDisplayName());
		};
	}
}
