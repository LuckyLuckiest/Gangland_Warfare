package me.luckyraven.command.sub.civilians;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

class CivilianSpawnerCommand extends SubArgument {

	private final Gangland             gangland;
	private final Tree<Argument>       tree;
	private final CivilianService      civilianService;
	private final CivilianSpawnManager civilianSpawnManager;

	CivilianSpawnerCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
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
