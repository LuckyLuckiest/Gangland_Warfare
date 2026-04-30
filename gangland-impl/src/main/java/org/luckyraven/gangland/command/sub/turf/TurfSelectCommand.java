package org.luckyraven.gangland.command.sub.turf;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.selection.Selection;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /glw turf select [id]} — marks a turf as the admin's active selection so follow-up
 * {@code /glw turf info|delete|setowner|status|show|tp} commands operate on it with no id argument.
 *
 * <ul>
 *   <li>No argument: selects the turf the admin is standing inside.</li>
 *   <li>Optional {@code <id>}: selects the turf with that id (shown by {@code /glw turf list}) — useful for jumping to
 *       a turf you're not currently in, then running {@code /glw turf tp} to warp there.</li>
 * </ul>
 */
class TurfSelectCommand extends SubArgument {

	private final Gangland             gangland;
	private final Tree<Argument>       tree;
	private final TurfManager          turfs;
	private final WandSelectionManager selections;
	private final TurfMessageContract  messages;

	protected TurfSelectCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                            TurfManager turfs, WandSelectionManager selections, TurfMessageContract messages) {
		super(gangland, "select", tree, parent);

		this.gangland   = gangland;
		this.tree       = tree;
		this.turfs      = turfs;
		this.selections = selections;
		this.messages   = messages;

		this.addSubArgument(idArgument());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				return;
			}
			Turf turf = turfs.findAt(player.getLocation());
			if (turf == null) {
				messages.send(sender, "TURF_NOT_INSIDE");
				return;
			}
			select(player, turf);
		};
	}

	private OptionalArgument idArgument() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				return;
			}
			// args = [turf, select, <id>]
			int id;
			try {
				id = Integer.parseInt(args[2]);
			} catch (NumberFormatException exception) {
				messages.send(sender, "TURF_NOT_FOUND", "turf", args[2]);
				return;
			}
			Turf turf = turfs.get(id);
			if (turf == null) {
				messages.send(sender, "TURF_NOT_FOUND", "turf", args[2]);
				return;
			}
			select(player, turf);
		}, sender -> {
			List<String> ids = new ArrayList<>();
			for (Turf turf : turfs.getAll()) {
				ids.add(String.valueOf(turf.getId()));
			}
			return ids;
		});
	}

	private void select(Player player, Turf turf) {
		Selection selection = selections.get(player);
		selection.setActiveTurfId(turf.getId());
		messages.send(player, "TURF_SELECTED",
		              "turf", turf.getDisplayName(),
		              "id", String.valueOf(turf.getId()));
	}
}
