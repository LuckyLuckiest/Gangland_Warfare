package org.luckyraven.gangland.civilians.command.spawner;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;

public class CivilianSpawnerCommand extends SubArgument {

	private final JavaPlugin             plugin;
	private final Tree<Argument>       tree;
	private final CivilianService      civilianService;
	private final CivilianSpawnManager civilianSpawnManager;

	public CivilianSpawnerCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                              CivilianService civilianService, CivilianSpawnManager civilianSpawnManager) {
		super(plugin, "spawner", tree, parent);

		this.plugin             = plugin;
		this.tree                 = tree;
		this.civilianService      = civilianService;
		this.civilianSpawnManager = civilianSpawnManager;

		initializeArguments();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			String message = GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(),
			                                               "<set/setgroup/remove/list/info/teleport>");
			sender.sendMessage(message);
		};
	}

	private void initializeArguments() {
		Argument setArg = new CivilianSpawnerSetCommand(plugin, tree, this, civilianService, civilianSpawnManager);
		Argument setGroupArg = new CivilianSpawnerSetGroupCommand(plugin, tree, this, civilianService,
		                                                          civilianSpawnManager);
		Argument removeArg   = new CivilianSpawnerRemoveCommand(plugin, tree, this, civilianSpawnManager);
		Argument listArg     = new CivilianSpawnerListCommand(plugin, tree, this, civilianSpawnManager);
		Argument infoArg     = new CivilianSpawnerInfoCommand(plugin, tree, this, civilianSpawnManager);
		Argument teleportArg = new CivilianSpawnerTeleportCommand(plugin, tree, this, civilianSpawnManager);

		List<Argument> arguments = new ArrayList<>();

		arguments.add(setArg);
		arguments.add(setGroupArg);
		arguments.add(removeArg);
		arguments.add(listArg);
		arguments.add(infoArg);
		arguments.add(teleportArg);

		this.addAllSubArguments(arguments);
	}
}
