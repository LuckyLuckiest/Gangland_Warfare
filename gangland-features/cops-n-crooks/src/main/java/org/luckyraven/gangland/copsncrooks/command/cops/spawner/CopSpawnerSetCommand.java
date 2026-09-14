package org.luckyraven.gangland.copsncrooks.command.cops.spawner;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;

class CopSpawnerSetCommand extends SubArgument {

	private final CopSpawnManager copSpawnManager;

	CopSpawnerSetCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, CopSpawnManager copSpawnManager) {
		super(plugin, "set", tree, parent);
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
