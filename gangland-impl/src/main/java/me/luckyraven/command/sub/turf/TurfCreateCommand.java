package me.luckyraven.command.sub.turf;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.turf.contract.TurfMessageContract;
import me.luckyraven.turf.data.CuboidRegion;
import me.luckyraven.turf.data.Turf;
import me.luckyraven.turf.manager.TurfManager;
import me.luckyraven.turf.selection.Selection;
import me.luckyraven.turf.selection.WandSelectionManager;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /glw turf create <displayName>}. The admin never types an id — it is auto-allocated by
 * {@link TurfManager#allocateId()}. The freshly-created turf becomes the admin's active selection so follow-up commands
 * need no id argument either.
 */
class TurfCreateCommand extends SubArgument {

	private final Gangland             gangland;
	private final Tree<Argument>       tree;
	private final TurfManager          turfs;
	private final WandSelectionManager selections;
	private final TurfMessageContract  messages;

	protected TurfCreateCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                            TurfManager turfs, WandSelectionManager selections, TurfMessageContract messages) {
		super(gangland, "create", tree, parent);

		this.gangland   = gangland;
		this.tree       = tree;
		this.turfs      = turfs;
		this.selections = selections;
		this.messages   = messages;

		this.addSubArgument(displayNameArgument());
	}

	private static String joinFrom(String[] args, int from) {
		StringBuilder builder = new StringBuilder();
		for (int i = from; i < args.length; i++) {
			if (i > from) {
				builder.append(' ');
			}
			builder.append(args[i]);
		}
		return builder.toString();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<displayName>"));
	}

	private OptionalArgument displayNameArgument() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				return;
			}
			// args = [turf, create, <displayName...>]
			if (args.length < 3) {
				sender.sendMessage(
						GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<displayName>"));
				return;
			}
			String displayName = joinFrom(args, 2);

			Selection selection = selections.get(player);
			if (!selection.isComplete() || selection.getPos1() == null || selection.getPos2() == null) {
				messages.send(sender, "TURF_CREATE_FAIL_NO_SELECTION");
				return;
			}
			Location pos1 = selection.getPos1();
			Location pos2 = selection.getPos2();
			if (pos1.getWorld() == null || pos2.getWorld() == null
			    || !pos1.getWorld().getName().equals(pos2.getWorld().getName())) {
				messages.send(sender, "TURF_CREATE_FAIL_CROSS_WORLD");
				return;
			}

			CuboidRegion region = new CuboidRegion(pos1.getWorld().getName(),
			                                       pos1.getBlockX(), pos1.getBlockZ(),
			                                       pos2.getBlockX(), pos2.getBlockZ());

			Turf conflict = turfs.findConflict(region);
			if (conflict != null) {
				messages.send(sender, "TURF_CREATE_FAIL_OVERLAP", "turf", conflict.getDisplayName());
				return;
			}

			int id = turfs.allocateId();
			Turf turf = new Turf(id, displayName, region, null,
			                     Settings.getTurfDefaultIncomeAmount(),
			                     System.currentTimeMillis(), 0L);
			turfs.create(turf);

			// Auto-select the new turf so follow-up commands operate on it with no id argument.
			selection.setActiveTurfId(id);

			messages.send(sender, "TURF_CREATE_SUCCESS",
			              "turf", displayName,
			              "id", String.valueOf(id),
			              "min", region.getMinX() + "," + region.getMinZ(),
			              "max", region.getMaxX() + "," + region.getMaxZ(),
			              "world", region.getWorld());
		}, sender -> List.of("<displayName>"));
	}
}
