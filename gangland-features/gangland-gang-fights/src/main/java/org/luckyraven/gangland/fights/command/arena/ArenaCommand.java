package org.luckyraven.gangland.fights.command.arena;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.keystone.command.argument.Argument;

import java.util.List;
import java.util.Map;

/**
 * Root of {@code /glw arena} (staff, {@code gangland.command.arena}). The verbs are one class each in this package;
 * the arena lane (W6-A) owns all of it from here.
 */
@CommandHandler
public final class ArenaCommand extends Command {

	public ArenaCommand(JavaPlugin plugin) {
		super(plugin, "arena", false);

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("arena"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender sender, String[] arguments) {
		help(sender, 1);
	}

	@Override
	protected void initializeArguments() {
		var plugin = getPlugin();
		var tree   = getArgumentTree();
		var root   = getArgument();

		root.addAllSubArguments(List.of(new ArenaCreateCommand(plugin, tree, root),
		                                new ArenaCornerCommand(plugin, tree, root),
		                                new ArenaSpawnCommand(plugin, tree, root),
		                                new ArenaExitCommand(plugin, tree, root),
		                                new ArenaSizeCommand(plugin, tree, root),
		                                new ArenaFinishCommand(plugin, tree, root),
		                                new ArenaShowCommand(plugin, tree, root),
		                                new ArenaListCommand(plugin, tree, root),
		                                new ArenaTpCommand(plugin, tree, root),
		                                new ArenaOnCommand(plugin, tree, root),
		                                new ArenaOffCommand(plugin, tree, root),
		                                new ArenaDeleteCommand(plugin, tree, root),
		                                new ArenaReloadCommand(plugin, tree, root)));
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Arena");
	}
}
