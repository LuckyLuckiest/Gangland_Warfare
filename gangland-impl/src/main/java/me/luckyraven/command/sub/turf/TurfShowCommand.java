package me.luckyraven.command.sub.turf;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.turf.contract.TurfMessageContract;
import me.luckyraven.turf.data.CuboidRegion;
import me.luckyraven.turf.data.Turf;
import me.luckyraven.turf.manager.TurfManager;
import me.luckyraven.turf.selection.Selection;
import me.luckyraven.turf.selection.WandSelectionManager;
import me.luckyraven.turf.task.TurfVisualization;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /glw turf show} — renders the particle outline for the admin's active-selection turf (or the one they are
 * standing inside). If neither exists but pos1/pos2 are set from the wand, previews that pending region so admins can
 * sanity-check their selection before running {@code /glw turf create}.
 */
class TurfShowCommand extends SubArgument {

	private final Gangland             gangland;
	private final TurfManager          turfs;
	private final WandSelectionManager selections;
	private final TurfMessageContract  messages;

	protected TurfShowCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                          TurfManager turfs, WandSelectionManager selections, TurfMessageContract messages) {
		super(gangland, "show", tree, parent);

		this.gangland   = gangland;
		this.turfs      = turfs;
		this.selections = selections;
		this.messages   = messages;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				return;
			}
			int    duration = Settings.getTurfVisualizationDurationSeconds();
			String particle = Settings.getTurfVisualizationParticle();

			Selection selection = selections.get(player);
			Integer   activeId  = selection.getActiveTurfId();
			if (activeId != null) {
				Turf active = turfs.get(activeId);
				if (active != null) {
					renderTurf(player, active, duration, particle);
					return;
				}
				selection.setActiveTurfId(null);
			}
			Turf standing = turfs.findAt(player.getLocation());
			if (standing != null) {
				selection.setActiveTurfId(standing.getId());
				renderTurf(player, standing, duration, particle);
				return;
			}
			if (selection.isComplete() && selection.getPos1() != null && selection.getPos2() != null) {
				renderSelection(player, selection, duration, particle);
				return;
			}
			messages.send(sender, "TURF_NOT_INSIDE");
		};
	}

	private void renderTurf(Player viewer, Turf turf, int seconds, String particle) {
		TurfVisualization.show(gangland, viewer, turf.getRegion().getWorld(), turf.getRegion(), seconds, particle);
		messages.send(viewer, "TURF_SHOW_STARTED",
		              "turf", turf.getDisplayName(),
		              "seconds", String.valueOf(seconds));
	}

	private void renderSelection(Player viewer, Selection selection, int seconds, String particle) {
		Location pos1 = selection.getPos1();
		Location pos2 = selection.getPos2();
		if (pos1 == null || pos2 == null || pos1.getWorld() == null) {
			return;
		}
		CuboidRegion region = new CuboidRegion(pos1.getWorld().getName(),
		                                       pos1.getBlockX(), pos1.getBlockZ(),
		                                       pos2.getBlockX(), pos2.getBlockZ());
		TurfVisualization.show(gangland, viewer, region.getWorld(), region, seconds, particle);
		messages.send(viewer, "TURF_SHOW_STARTED",
		              "turf", "pending selection",
		              "seconds", String.valueOf(seconds));
	}
}
