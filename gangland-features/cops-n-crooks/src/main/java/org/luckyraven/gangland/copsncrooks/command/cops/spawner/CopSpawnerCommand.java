package org.luckyraven.gangland.copsncrooks.command.cops.spawner;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.command.CommandMessages;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.station.StationRegistry;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.ArrayList;
import java.util.List;

public class CopSpawnerCommand extends SubArgument {

	private final JavaPlugin        plugin;
	private final Tree<Argument>  tree;
	private final CopSpawnManager copSpawnManager;
	private final StationRegistry stations;
	private final CopLoader       copLoader;
	private final CommandMessages commandMessages;

	public CopSpawnerCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, CopSpawnManager copSpawnManager,
	                         StationRegistry stations, CopLoader copLoader, CommandMessages commandMessages) {
		super(plugin, "spawner", tree, parent);

		this.plugin          = plugin;
		this.tree            = tree;
		this.copSpawnManager = copSpawnManager;
		this.stations        = stations;
		this.copLoader       = copLoader;
		this.commandMessages = commandMessages;

		initializeArguments();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(commandMessages.usage("/glw cop spawner <set|remove|list|info|teleport>"));
		};
	}

	private void initializeArguments() {
		Argument setArg      = new CopSpawnerSetCommand(plugin, tree, this, copSpawnManager, stations, copLoader,
		                                                       commandMessages);
		Argument removeArg   = new CopSpawnerRemoveCommand(plugin, tree, this, copSpawnManager, commandMessages);
		Argument listArg     = new CopSpawnerListCommand(plugin, tree, this, copSpawnManager, commandMessages);
		Argument infoArg     = new CopSpawnerInfoCommand(plugin, tree, this, copSpawnManager, commandMessages);
		Argument teleportArg = new CopSpawnerTeleportCommand(plugin, tree, this, copSpawnManager, commandMessages);

		List<Argument> arguments = new ArrayList<>();

		arguments.add(setArg);
		arguments.add(removeArg);
		arguments.add(listArg);
		arguments.add(infoArg);
		arguments.add(teleportArg);

		this.addAllSubArguments(arguments);
	}
}
