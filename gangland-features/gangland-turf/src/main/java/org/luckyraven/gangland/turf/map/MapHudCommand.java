package org.luckyraven.gangland.turf.map;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;
import org.bukkit.command.CommandSender;

/**
 * {@code /glw map hud} (0.17.0 stub): answers {@code Map.Not_Ready} until its lane fills the body in.
 */
public final class MapHudCommand extends SubArgument {

	private final MapPrefsService prefs;
	private final TurfMessages    text;

	public MapHudCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, MapPrefsService prefs, TurfMessages text) {
		super(plugin, "hud", tree, parent);
		this.prefs = prefs;
		this.text  = text;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				text.text("Map.Not_Ready", "&7This part of the map is not ready yet."));
	}
}
