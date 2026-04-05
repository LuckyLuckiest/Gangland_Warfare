package me.luckyraven.command.sub.civilians;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class CivilianSpawnerSetCommand extends SubArgument {

	private final Gangland gangland;

	CivilianSpawnerSetCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
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

			CivilianSpawnManager spawnManager = gangland.getInitializer().getCivilianSpawnManager();
			spawnManager.setSpawnerLocation(player.getLocation());

			sender.sendMessage(GanglandChatUtil.commandMessage("&aCivilian spawner set at your location."));
		};
	}
}
