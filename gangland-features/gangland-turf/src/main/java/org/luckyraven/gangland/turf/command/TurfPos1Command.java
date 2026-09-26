package org.luckyraven.gangland.turf.command;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.turf.selection.Selection;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;

class TurfPos1Command extends SubArgument {

	private final WandSelectionManager selections;

	protected TurfPos1Command(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                          WandSelectionManager selections) {
		super(plugin, "pos1", tree, parent);

		this.selections = selections;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			// GI-35: see TurfWandCommand — pos1/pos2 chat commands must not bypass the admin gate the physical
			// wand click already enforces (WandListener).
			if (!sender.hasPermission(WandSelectionManager.ADMIN_PERMISSION)) {
				sender.sendMessage(Messages.COMMAND_NO_PERM.toString());
				return;
			}
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
