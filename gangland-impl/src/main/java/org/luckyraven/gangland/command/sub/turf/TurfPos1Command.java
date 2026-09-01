package org.luckyraven.gangland.command.sub.turf;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.turf.selection.Selection;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;

class TurfPos1Command extends SubArgument {

	private final WandSelectionManager selections;

	protected TurfPos1Command(Gangland gangland, Tree<Argument> tree, Argument parent,
	                          WandSelectionManager selections) {
		super(gangland, "pos1", tree, parent);

		this.selections = selections;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				return;
			}
			Location  location  = player.getLocation();
			Selection selection = selections.get(player);
			selection.set(location, true);

			String reply = Messages.TURF_POS_SET.toString()
			                                    .replace("%corner%", "pos1")
			                                    .replace("%x%", Integer.toString(location.getBlockX()))
			                                    .replace("%y%", Integer.toString(location.getBlockY()))
			                                    .replace("%z%", Integer.toString(location.getBlockZ()))
			                                    .replace("%world%",
			                                             location.getWorld() == null
			                                             ? "unknown"
			                                             : location.getWorld().getName());
			player.sendMessage(reply);
		};
	}
}
