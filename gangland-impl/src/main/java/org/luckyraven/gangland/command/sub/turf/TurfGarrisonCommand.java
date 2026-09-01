package org.luckyraven.gangland.command.sub.turf;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.List;

/**
 * {@code /glw turf garrison <count>} — admin sets the per-turf garrison stock to an exact count for the
 * active-selection turf. {@code count = 0} clears the stock; positive values overwrite (not add). Used both for testing
 * the garrison-deploy flow and for moderator-correcting a turf whose stock got out of sync.
 *
 * <p>Bare {@code /glw turf garrison} (no count) prints the current stock without modifying it.
 */
class TurfGarrisonCommand extends SubArgument {

	private final Gangland             gangland;
	private final Tree<Argument>       tree;
	private final TurfManager          turfs;
	private final WandSelectionManager selections;
	private final TurfMessageContract  messages;
	private final GarrisonManager      garrisons;

	protected TurfGarrisonCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                              TurfManager turfs, WandSelectionManager selections,
	                              TurfMessageContract messages, GarrisonManager garrisons) {
		super(gangland, "garrison", tree, parent);

		this.gangland   = gangland;
		this.tree       = tree;
		this.turfs      = turfs;
		this.selections = selections;
		this.messages   = messages;
		this.garrisons  = garrisons;

		this.addSubArgument(countArgument());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Turf turf = TurfSelectionResolver.resolve(sender, turfs, selections, messages);
			if (turf == null) return;
			messages.send(sender, "TURF_GARRISON_VIEW",
			              "turf", turf.getDisplayName(),
			              "count", String.valueOf(garrisons.count(turf.getId())));
		};
	}

	private OptionalArgument countArgument() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Turf turf = TurfSelectionResolver.resolve(sender, turfs, selections, messages);
			if (turf == null) return;
			int count;
			try {
				count = Integer.parseInt(args[2]);
			} catch (NumberFormatException exception) {
				sender.sendMessage(GanglandChatUtil.setArguments(
						Messages.ARGUMENTS_MISSING.toString(), "<count>"));
				return;
			}
			if (count < 0) {
				sender.sendMessage(GanglandChatUtil.setArguments(
						Messages.ARGUMENTS_MISSING.toString(), "<count>"));
				return;
			}
			int current = garrisons.count(turf.getId());
			if (count > current) {
				garrisons.add(turf.getId(), count - current);
			} else if (count < current) {
				garrisons.consume(turf.getId(), current - count);
			}
			messages.send(sender, "TURF_GARRISON_SET",
			              "turf", turf.getDisplayName(),
			              "count", String.valueOf(count));
		}, sender -> List.of("0", "1", "3", "5", "10"));
	}
}
