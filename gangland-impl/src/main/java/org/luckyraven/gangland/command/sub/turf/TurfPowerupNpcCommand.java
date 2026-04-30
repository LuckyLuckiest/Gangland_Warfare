package org.luckyraven.gangland.command.sub.turf;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.copsncrooks.npc.turf.TurfPowerupManager;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.List;

/**
 * {@code /glw turf powerupnpc set|remove} — admin places (at sender location) or removes the per-turf Quartermaster NPC
 * for the active-selection turf. The active-selection turf comes from {@link TurfSelectionResolver} (wand-selected,
 * last-tp'd, or "standing inside").
 */
class TurfPowerupNpcCommand extends SubArgument {

	private static final String ACTION_SET    = "set";
	private static final String ACTION_REMOVE = "remove";

	private final Gangland             gangland;
	private final Tree<Argument>       tree;
	private final TurfManager          turfs;
	private final WandSelectionManager selections;
	private final TurfMessageContract  messages;
	private final TurfPowerupManager   powerupNpcs;

	protected TurfPowerupNpcCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                                TurfManager turfs, WandSelectionManager selections,
	                                TurfMessageContract messages, TurfPowerupManager powerupNpcs) {
		super(gangland, "powerupnpc", tree, parent);

		this.gangland    = gangland;
		this.tree        = tree;
		this.turfs       = turfs;
		this.selections  = selections;
		this.messages    = messages;
		this.powerupNpcs = powerupNpcs;

		this.addSubArgument(actionArgument());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<set|remove>"));
	}

	private OptionalArgument actionArgument() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Turf turf = TurfSelectionResolver.resolve(sender, turfs, selections, messages);
			if (turf == null) return;

			String action = args[2];
			if (ACTION_SET.equalsIgnoreCase(action)) {
				if (!(sender instanceof Player player)) {
					messages.send(sender, "TURF_NOT_INSIDE");
					return;
				}
				powerupNpcs.place(turf.getId(), player.getLocation(), null);
				messages.send(sender, "TURF_POWERUPNPC_SET", "turf", turf.getDisplayName());
			} else if (ACTION_REMOVE.equalsIgnoreCase(action)) {
				powerupNpcs.remove(turf.getId());
				messages.send(sender, "TURF_POWERUPNPC_REMOVED", "turf", turf.getDisplayName());
			} else {
				sender.sendMessage(GanglandChatUtil.setArguments(
						Messages.ARGUMENTS_MISSING.toString(), "<set|remove>"));
			}
		}, sender -> List.of(ACTION_SET, ACTION_REMOVE));
	}
}
