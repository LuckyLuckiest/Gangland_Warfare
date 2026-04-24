package me.luckyraven.command.sub.turf;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.turf.selection.Selection;
import me.luckyraven.turf.selection.WandSelectionManager;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class TurfPos2Command extends SubArgument {

	private final WandSelectionManager selections;

	protected TurfPos2Command(Gangland gangland, Tree<Argument> tree, Argument parent,
	                          WandSelectionManager selections) {
		super(gangland, "pos2", tree, parent);

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
			selection.set(location, false);

			String reply = Messages.TURF_POS_SET.toString()
			                                    .replace("%corner%", "pos2")
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
