package org.luckyraven.gangland.turf.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;

/**
 * {@code /glw turf delete} — removes the admin's active-selection turf (or the one they are standing inside if no
 * selection is set). Clears the active selection afterwards since it points at a now-deleted turf.
 *
 * <p>GI-34 (TF-03): also cascades the delete to the turf's garrison stock, active buffs and Quartermaster NPC — none
 * of those child rows are foreign-keyed to the turf, so without this they survived under the deleted id and were
 * inherited by whichever new turf later reused it (TF-05, {@code TurfManager}'s {@code nextId} re-seeds to
 * {@code max(existing)+1}).
 */
class TurfDeleteCommand extends SubArgument {

	private final TurfManager          turfs;
	private final WandSelectionManager selections;
	private final TurfMessageContract  messages;
	private final GarrisonManager      garrisons;
	private final ActiveBuffManager    buffs;
	private final TurfPowerupManager   powerupNpcs;

	protected TurfDeleteCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                            TurfManager turfs, WandSelectionManager selections, TurfMessageContract messages,
	                            GarrisonManager garrisons, ActiveBuffManager buffs, TurfPowerupManager powerupNpcs) {
		super(plugin, "delete", tree, parent);

		this.turfs       = turfs;
		this.selections  = selections;
		this.messages    = messages;
		this.garrisons   = garrisons;
		this.buffs       = buffs;
		this.powerupNpcs = powerupNpcs;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			// GI-35: deleting a turf is an admin-only mutation — nested SubArgument permissions only add onto the
			// base "gangland.command.turf" node, they don't require this stricter one on their own.
			if (!sender.hasPermission(WandSelectionManager.ADMIN_PERMISSION)) {
				sender.sendMessage(Messages.COMMAND_NO_PERM.toString());
				return;
			}
			Turf turf = TurfSelectionResolver.resolve(sender, turfs, selections, messages);
			if (turf == null) {
				return;
			}
			turfs.delete(turf);
			garrisons.remove(turf.getId());
			buffs.removeAll(turf.getId());
			powerupNpcs.remove(turf.getId());
			if (sender instanceof Player player) {
				selections.get(player).setActiveTurfId(null);
			}
			messages.send(sender, "TURF_DELETE_SUCCESS", "turf", turf.getDisplayName());
		};
	}
}
