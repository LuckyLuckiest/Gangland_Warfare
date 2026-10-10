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
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.selection.Selection;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;

class TurfPos2Command extends SubArgument {

	private final WandSelectionManager selections;
	private final TurfMessages         text;

	protected TurfPos2Command(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                          WandSelectionManager selections,
	                          TurfMessages text) {
		super(plugin, "pos2", tree, parent);

		this.selections = selections;
		this.text       = text;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			// GI-35: see TurfWandCommand — pos1/pos2 chat commands must not bypass the admin gate the physical
			// wand click already enforces (WandListener).
			if (!TurfPermissions.requireAdmin(sender, text)) {
				return;
			}
			if (!(sender instanceof Player player)) {
				return;
			}
			Location  location  = player.getLocation();
			Selection selection = selections.get(player);
			if (!selection.set(location, false)) {
				player.sendMessage(text.text("Turf.Selection_Other_World", Selection.OTHER_WORLD_FALLBACK));
				return;
			}

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
