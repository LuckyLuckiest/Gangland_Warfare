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
 * {@code /glw map text}: the nearest turfs of the viewer's world, one clickable line each.
 */
public final class MapTextCommand extends SubArgument {

	private final MapPrefsService prefs;
	private final TurfMessages    text;
	private final TurfMapService  map;

	public MapTextCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, MapPrefsService prefs,
	                      TurfMessages text, TurfMapService map) {
		super(plugin, "text", tree, parent);
		this.prefs = prefs;
		this.text  = text;
		this.map   = map;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (sender instanceof Player player) {
				map.sendText(player);
			}
		};
	}
}
