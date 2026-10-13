package org.luckyraven.gangland.fights.command.arena;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.fights.FightRules;
import org.luckyraven.gangland.fights.arena.ArenaDrafts;
import org.luckyraven.gangland.fights.arena.ArenaMessages;
import org.luckyraven.gangland.fights.arena.ArenaRegistry;
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

	private final ArenaDeps deps;

	public ArenaCommand(JavaPlugin plugin, ArenaRegistry registry, ArenaDrafts drafts, ArenaMessages text,
	                    FightRules rules) {
		super(plugin, "arena", false);
		this.deps = new ArenaDeps(registry, drafts, text, rules);

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

		root.addAllSubArguments(List.of(new ArenaCreateCommand(plugin, tree, root, deps),
		                                new ArenaCornerCommand(plugin, tree, root, deps),
		                                new ArenaSpawnCommand(plugin, tree, root, deps),
		                                new ArenaExitCommand(plugin, tree, root, deps),
		                                new ArenaSizeCommand(plugin, tree, root, deps),
		                                new ArenaFinishCommand(plugin, tree, root, deps),
		                                new ArenaShowCommand(plugin, tree, root, deps),
		                                new ArenaListCommand(plugin, tree, root, deps),
		                                new ArenaTpCommand(plugin, tree, root, deps),
		                                new ArenaOnCommand(plugin, tree, root, deps),
		                                new ArenaOffCommand(plugin, tree, root, deps),
		                                new ArenaDeleteCommand(plugin, tree, root, deps),
		                                new ArenaReloadCommand(plugin, tree, root, deps)));
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Arena");
	}
}
