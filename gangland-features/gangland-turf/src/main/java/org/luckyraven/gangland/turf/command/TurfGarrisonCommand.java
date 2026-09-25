package org.luckyraven.gangland.turf.command;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
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

import java.util.List;

/**
 * {@code /glw turf garrison <count>} — admin sets the per-turf garrison stock to an exact count for the
 * active-selection turf. {@code count = 0} clears the stock; positive values overwrite (not add). Used both for testing
 * the garrison-deploy flow and for moderator-correcting a turf whose stock got out of sync.
 *
 * <p>Bare {@code /glw turf garrison} (no count) prints the current stock without modifying it.
 */
class TurfGarrisonCommand extends SubArgument {

	private final JavaPlugin             plugin;
	private final Tree<Argument>       tree;
	private final TurfManager          turfs;
	private final WandSelectionManager selections;
	private final TurfMessageContract  messages;
	private final GarrisonManager      garrisons;

	protected TurfGarrisonCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                              TurfManager turfs, WandSelectionManager selections,
	                              TurfMessageContract messages, GarrisonManager garrisons) {
		super(plugin, "garrison", tree, parent);

		this.plugin   = plugin;
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

	// Package-private (not private) so TurfGarrisonCommandTest can pin GI-40 directly.
	static Integer parseCount(String raw) {
		try {
			return Integer.parseInt(raw);
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private OptionalArgument countArgument() {
		return new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			// GI-35: nested SubArgument permissions only add onto the base "gangland.command.turf" node — they
			// never require the stricter admin one on their own.
			if (!sender.hasPermission(WandSelectionManager.ADMIN_PERMISSION)) {
				sender.sendMessage(Messages.COMMAND_NO_PERM.toString());
				return;
			}
			Turf turf = TurfSelectionResolver.resolve(sender, turfs, selections, messages);
			if (turf == null) return;
			// GI-40: an argument WAS supplied here, just invalid — TURF_GARRISON_INVALID (mirroring
			// TurfIncomeCommand's TURF_INCOME_INVALID) says so instead of the generic "missing arguments".
			Integer count = parseCount(args[2]);
			if (count == null || count < 0) {
				messages.send(sender, "TURF_GARRISON_INVALID", "count", args[2]);
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
