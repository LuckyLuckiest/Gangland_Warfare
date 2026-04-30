package org.luckyraven.gangland.command.sub.turf;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /glw turf tp [id]} — teleports the admin to the centre of a turf, standing on the highest block at that
 * column.
 *
 * <ul>
 *   <li>No argument: uses the admin's active selection (set by {@code /glw turf create}, {@code /glw turf select},
 *       or a previous tp).</li>
 *   <li>With {@code <id>}: teleports directly to that turf. This is the target of the clickable {@code (tp)} button
 *       on {@code /glw turf list} rows, so admins never have to type the id themselves.</li>
 * </ul>
 */
class TurfTpCommand extends SubArgument {

	private final Gangland             gangland;
	private final Tree<Argument>       tree;
	private final TurfManager          turfs;
	private final WandSelectionManager selections;
	private final TurfMessageContract  messages;

	protected TurfTpCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                        TurfManager turfs, WandSelectionManager selections, TurfMessageContract messages) {
		super(gangland, "tp", tree, parent);

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
			Turf turf = TurfSelectionResolver.resolve(sender, turfs, selections, messages);
			if (turf == null) {
				return;
			}
			teleport(player, turf);
		};
	}

	private OptionalArgument idArgument() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				return;
			}
			// args = [turf, tp, <id>]
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
			teleport(player, turf);
		}, sender -> {
			List<String> ids = new ArrayList<>();
			for (Turf turf : turfs.getAll()) {
				ids.add(String.valueOf(turf.getId()));
			}
			return ids;
		});
	}

	private void teleport(Player player, Turf turf) {
		CuboidRegion region = turf.getRegion();
		World        world  = Bukkit.getWorld(region.getWorld());
		if (world == null) {
			messages.send(player, "TURF_NOT_FOUND", "turf", turf.getDisplayName());
			return;
		}
		double centreX = (region.getMinX() + region.getMaxX()) / 2.0 + 0.5;
		double centreZ = (region.getMinZ() + region.getMaxZ()) / 2.0 + 0.5;
		int    y       = world.getHighestBlockYAt((int) centreX, (int) centreZ);
		Location target = new Location(world, centreX, y + 1.0, centreZ,
		                               player.getLocation().getYaw(), player.getLocation().getPitch());
		player.teleport(target);

		// Teleporting engages that turf — set it as the active selection so subsequent info/delete/etc. chain.
		selections.get(player).setActiveTurfId(turf.getId());

		messages.send(player, "TURF_TP_SUCCESS",
		              "turf", turf.getDisplayName(),
		              "id", String.valueOf(turf.getId()));
	}
}
