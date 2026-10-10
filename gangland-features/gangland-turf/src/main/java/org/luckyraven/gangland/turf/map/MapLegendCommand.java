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
 * {@code /glw map legend}: one line per gang in view with its glyph, name, relation and state.
 */
public final class MapLegendCommand extends SubArgument {

	private final MapPrefsService prefs;
	private final TurfMessages    text;
	private final TurfMapService  map;

	public MapLegendCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, MapPrefsService prefs,
	                        TurfMessages text, TurfMapService map) {
		super(plugin, "legend", tree, parent);
		this.prefs = prefs;
		this.text  = text;
		this.map   = map;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (sender instanceof Player player) {
				map.sendLegend(player);
			}
		};
	}
}
