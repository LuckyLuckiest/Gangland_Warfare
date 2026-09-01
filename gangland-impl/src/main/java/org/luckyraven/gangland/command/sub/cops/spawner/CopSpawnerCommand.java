package org.luckyraven.gangland.command.sub.cops.spawner;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;

public class CopSpawnerCommand extends SubArgument {

	private final Gangland        gangland;
	private final Tree<Argument>  tree;
	private final CopSpawnManager copSpawnManager;

	public CopSpawnerCommand(Gangland gangland, Tree<Argument> tree, Argument parent, CopSpawnManager copSpawnManager) {
		super(gangland, "spawner", tree, parent);

		this.gangland        = gangland;
		this.tree            = tree;
		this.copSpawnManager = copSpawnManager;

		initializeArguments();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			String message = GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(),
			                                               "<set/remove/list/info/teleport>");
			sender.sendMessage(message);
		};
	}

	private void initializeArguments() {
		Argument setArg      = new CopSpawnerSetCommand(gangland, tree, this, copSpawnManager);
		Argument removeArg   = new CopSpawnerRemoveCommand(gangland, tree, this, copSpawnManager);
		Argument listArg     = new CopSpawnerListCommand(gangland, tree, this, copSpawnManager);
		Argument infoArg     = new CopSpawnerInfoCommand(gangland, tree, this, copSpawnManager);
		Argument teleportArg = new CopSpawnerTeleportCommand(gangland, tree, this, copSpawnManager);

		List<Argument> arguments = new ArrayList<>();

		arguments.add(setArg);
		arguments.add(removeArg);
		arguments.add(listArg);
		arguments.add(infoArg);
		arguments.add(teleportArg);

		this.addAllSubArguments(arguments);
	}
}
