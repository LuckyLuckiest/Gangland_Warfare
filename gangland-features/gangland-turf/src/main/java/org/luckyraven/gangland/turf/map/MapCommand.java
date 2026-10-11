package org.luckyraven.gangland.turf.map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.manager.TurfManager;

import java.util.List;
import java.util.Map;

/**
 * Root of the {@code /glw map} command tree (0.17.0). The root sends the chat map (W1-M); the sub-commands are
 * {@code auto}, {@code hud} (W1-H), {@code text} and {@code legend}.
 */
@CommandHandler
public final class MapCommand extends Command {

	private final MapPrefsService prefs;
	private final TurfMessages    text;
	private final TurfMapService  map;
	private final TurfManager     turfs;
	private final TurfSettings    settings;

	public MapCommand(JavaPlugin plugin, MapPrefsService prefs, TurfMessages text, TurfMapService map,
	                  TurfManager turfs, TurfSettings settings) {
		super(plugin, "map", true);
		this.prefs = prefs;
		this.text  = text;
		this.map   = map;
		this.turfs    = turfs;
		this.settings = settings;

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
		if (sender instanceof Player player) {
			map.send(player);
		}
	}

	@Override
	protected void initializeArguments() {
		MapAutoCommand   auto   = new MapAutoCommand(getPlugin(), getArgumentTree(), getArgument(), prefs, text);
		MapHudCommand    hud    = new MapHudCommand(getPlugin(), getArgumentTree(), getArgument(), prefs, text);
		MapTextCommand   txt    = new MapTextCommand(getPlugin(), getArgumentTree(), getArgument(), map);
		MapLegendCommand legend = new MapLegendCommand(getPlugin(), getArgumentTree(), getArgument(), map);
		getArgument().addAllSubArguments(List.of(auto, hud, txt, legend));
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Map");
	}
}
