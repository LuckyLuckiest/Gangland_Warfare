package org.luckyraven.gangland.copsncrooks.command.cops.spawner;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.copsncrooks.command.CommandMessages;

import java.util.Map;

class CopSpawnerRemoveCommand extends SubArgument {

	private final JavaPlugin        plugin;
	private final Tree<Argument>  tree;
	private final CopSpawnManager copSpawnManager;
	private final CommandMessages commandMessages;

	CopSpawnerRemoveCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, CopSpawnManager copSpawnManager,
	                        CommandMessages commandMessages) {
		super(plugin, "remove", tree, parent);

		this.plugin        = plugin;
		this.tree            = tree;
		this.copSpawnManager = copSpawnManager;
		this.commandMessages = commandMessages;

		idArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(commandMessages.usage("/glw cop spawner remove <id>"));
		};
	}

	private void idArgument() {
		Argument idArg = new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			String idStr = args[3];
			int    id;
			try {
				id = Integer.parseInt(idStr);
			} catch (NumberFormatException e) {
				sender.sendMessage(commandMessages.format(CommandMessages.Key.BAD_ID, Map.of("value", idStr)));
				return;
			}

			Location location = copSpawnManager.getSpawnerLocation(id);
			if (location == null) {
				sender.sendMessage(commandMessages.format(CommandMessages.Key.SPAWNER_UNKNOWN, Map.of("id", idStr)));
				return;
			}

			copSpawnManager.removeSpawner(id);

			sender.sendMessage(commandMessages.format(CommandMessages.Key.SPAWNER_REMOVED,
                                                      Map.of("id", String.valueOf(id))));
		}, sender -> copSpawnManager.getSpawnerIds()
				.stream().map(String::valueOf).toList());

		this.addSubArgument(idArg);
	}
}
