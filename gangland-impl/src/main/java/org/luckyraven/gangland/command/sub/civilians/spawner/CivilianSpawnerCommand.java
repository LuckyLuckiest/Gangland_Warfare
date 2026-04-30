package org.luckyraven.gangland.command.sub.civilians.spawner;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.copsncrooks.npc.civilian.CivilianService;
import org.luckyraven.gangland.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;

public class CivilianSpawnerCommand extends SubArgument {

	private final Gangland             gangland;
	private final Tree<Argument>       tree;
	private final CivilianService      civilianService;
	private final CivilianSpawnManager civilianSpawnManager;

	public CivilianSpawnerCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                              CivilianService civilianService, CivilianSpawnManager civilianSpawnManager) {
		super(gangland, "spawner", tree, parent);

		this.gangland             = gangland;
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
		Argument setArg = new CivilianSpawnerSetCommand(gangland, tree, this, civilianService, civilianSpawnManager);
		Argument setGroupArg = new CivilianSpawnerSetGroupCommand(gangland, tree, this, civilianService,
		                                                          civilianSpawnManager);
		Argument removeArg   = new CivilianSpawnerRemoveCommand(gangland, tree, this, civilianSpawnManager);
		Argument listArg     = new CivilianSpawnerListCommand(gangland, tree, this, civilianSpawnManager);
		Argument infoArg     = new CivilianSpawnerInfoCommand(gangland, tree, this, civilianSpawnManager);
		Argument teleportArg = new CivilianSpawnerTeleportCommand(gangland, tree, this, civilianSpawnManager);

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
