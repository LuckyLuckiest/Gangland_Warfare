package org.luckyraven.gangland.turf.map;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.gangland.turf.config.TurfMessages;

import java.util.List;
import java.util.Map;

/**
 * Root of the {@code /glw map} command tree (0.17.0). The map grid itself is W1-M's and is not built yet, so the root
 * answers {@code Map.Not_Ready}; the four sub-commands are stubs until their lanes fill them.
 */
@CommandHandler
public final class MapCommand extends Command {

	private final MapPrefsService prefs;
	private final TurfMessages    text;

	public MapCommand(JavaPlugin plugin, MapPrefsService prefs, TurfMessages text) {
		super(plugin, "map", true);
		this.prefs = prefs;
		this.text  = text;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("map"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender sender, String[] arguments) {
		sender.sendMessage(text.text("Map.Not_Ready", "&7This part of the map is not ready yet."));
	}

	@Override
	protected void initializeArguments() {
		MapAutoCommand   auto   = new MapAutoCommand(getPlugin(), getArgumentTree(), getArgument(), prefs, text);
		MapHudCommand    hud    = new MapHudCommand(getPlugin(), getArgumentTree(), getArgument(), prefs, text);
		MapTextCommand   txt    = new MapTextCommand(getPlugin(), getArgumentTree(), getArgument(), prefs, text);
		MapLegendCommand legend = new MapLegendCommand(getPlugin(), getArgumentTree(), getArgument(), prefs, text);
		getArgument().addAllSubArguments(List.of(auto, hud, txt, legend));
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Map");
	}
}
