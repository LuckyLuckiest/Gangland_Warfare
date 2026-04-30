package org.luckyraven.gangland.command.sub.cops.spawner;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;

class CopSpawnerSetCommand extends SubArgument {

	private final CopSpawnManager copSpawnManager;

	CopSpawnerSetCommand(Gangland gangland, Tree<Argument> tree, Argument parent, CopSpawnManager copSpawnManager) {
		super(gangland, "set", tree, parent);
		this.copSpawnManager = copSpawnManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return;
			}

			copSpawnManager.setSpawnerLocation(player.getLocation());

			sender.sendMessage(Messages.COP_SPAWNER_SET.toString());
		};
	}
}
