package org.luckyraven.gangland.turf.map;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/**
 * {@code /glw map border}: a short particle outline of the turf the player stands in, or the nearest one, clipped
 * to the part near the player. Stub registered by the gt2 kickoff; lane W3-V writes the body.
 */
public final class MapBorderCommand extends SubArgument {

	private final JavaPlugin   plugin;
	private final TurfManager  turfs;
	private final TurfSettings settings;
	private final TurfMessages text;

	public MapBorderCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, TurfManager turfs,
	                        TurfSettings settings, TurfMessages text) {
		super(plugin, "border", tree, parent);
		this.plugin   = plugin;
		this.turfs    = turfs;
		this.settings = settings;
		this.text     = text;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
		};
	}
}
