package org.luckyraven.gangland.turf.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.view.QuartermasterPlacement;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/**
 * {@code /glw turf quartermaster}: the player verb (D29=B) that opens the owner-placement confirm menu for the turf
 * the player stands in. Everything else (ownership, rank, Citizens, price) is checked by {@link QuartermasterPlacement}.
 */
class TurfQuartermasterCommand extends SubArgument {

	private final TurfManager           turfs;
	private final TurfMessageContract   messages;
	private final QuartermasterPlacement placement;

	protected TurfQuartermasterCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, TurfManager turfs,
	                                   TurfMessageContract messages, QuartermasterPlacement placement) {
		super(plugin, "quartermaster", tree, parent);

		this.turfs     = turfs;
		this.messages  = messages;
		this.placement = placement;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				messages.send(sender, "TURF_NOT_INSIDE");
				return;
			}
			Turf turf = turfs.findAt(player.getLocation());
			if (turf == null) {
				messages.send(sender, "TURF_NOT_INSIDE");
				return;
			}
			placement.open(player, turf);
		};
	}
}
