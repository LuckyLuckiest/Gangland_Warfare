package me.luckyraven.command.sub.copsncrooks;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.copsncrooks.police.spawn.CopSpawnManager;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class CopSpawnerSetCommand extends SubArgument {

	private final Gangland gangland;

	CopSpawnerSetCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "set", tree, parent);

		this.gangland = gangland;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(ChatUtil.commandMessage("&cOnly players can use this command."));
				return;
			}

			CopSpawnManager copSpawnManager = gangland.getInitializer().getCopSpawnManager();
			copSpawnManager.setSpawnerLocation(player.getLocation());

			sender.sendMessage(ChatUtil.commandMessage("&aCop spawner set at your location."));
		};
	}
}
