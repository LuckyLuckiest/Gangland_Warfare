package me.luckyraven.command.sub.turf;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.turf.contract.TurfMessageContract;
import me.luckyraven.turf.data.Turf;
import me.luckyraven.turf.manager.TurfManager;
import me.luckyraven.turf.powerups.GarrisonManager;
import me.luckyraven.turf.selection.WandSelectionManager;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;

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
