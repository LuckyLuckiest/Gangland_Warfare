package me.luckyraven.command.sub.cops;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.copsncrooks.police.spawn.CopSpawnManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
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
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return;
			}

			CopSpawnManager copSpawnManager = gangland.getInitializer().getCopSpawnManager();
			copSpawnManager.setSpawnerLocation(player.getLocation());

			sender.sendMessage(GanglandChatUtil.commandMessage("&aCop spawner set at your location."));
		};
	}
}
