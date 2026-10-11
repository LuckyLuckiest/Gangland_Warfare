package org.luckyraven.gangland.turf.map;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/**
 * {@code /glw map zoom in|out}: the player's own map cell size (8, 16, 32 or 64 blocks). Stub registered by the gt2
 * kickoff; lane W3-Z writes the body.
 */
public final class MapZoomCommand extends SubArgument {

	private final JavaPlugin      plugin;
	private final Tree<Argument>  tree;
	private final MapPrefsService prefs;
	private final TurfMessages    text;

	public MapZoomCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, MapPrefsService prefs,
	                      TurfMessages text) {
		super(plugin, "zoom", tree, parent);
		this.plugin = plugin;
		this.tree   = tree;
		this.prefs  = prefs;
		this.text   = text;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
		};
	}
}
