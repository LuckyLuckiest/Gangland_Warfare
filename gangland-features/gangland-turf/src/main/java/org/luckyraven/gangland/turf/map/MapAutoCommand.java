package org.luckyraven.gangland.turf.map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/**
 * {@code /glw map auto}: toggles the auto map. Turning it on sends the map on the next {@link MapAutoTask} tick
 * (within a second); the task then re-sends it while what it shows changes, at most every
 * {@code Map.Auto_Min_Interval_Seconds}.
 */
public final class MapAutoCommand extends SubArgument {

	private final MapPrefsService prefs;
	private final TurfMessages    text;

	public MapAutoCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, MapPrefsService prefs,
	                      TurfMessages text) {
		super(plugin, "auto", tree, parent);
		this.prefs = prefs;
		this.text  = text;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				return;
			}
			boolean on = !prefs.isAuto(player.getUniqueId());
			prefs.setAuto(player.getUniqueId(), on);
			if (on) {
				sender.sendMessage(text.text("Map.Auto_On", "&7Auto map &aon&7: the map is sent again when what it shows changes."));
			} else {
				sender.sendMessage(text.text("Map.Auto_Off", "&7Auto map &coff&7."));
			}
		};
	}
}
