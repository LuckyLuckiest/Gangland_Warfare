package me.luckyraven.command.sub.civilians;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.civilian.npc.CivilianNpc;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;

class CivilianSpawnCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;

	CivilianSpawnCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "spawn", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		typeIdArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<typeId>"));
		};
	}

	private void typeIdArgument() {
		Argument typeArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return;
			}

			String               typeId       = args[2];
			CivilianSpawnManager spawnManager = gangland.getInitializer().getCivilianSpawnManager();
			CivilianNpc          npc          = spawnManager.spawnNearLocation(player, typeId);

			if (npc == null) {
				sender.sendMessage(GanglandChatUtil.commandMessage(
						"&cFailed to spawn civilian. Check that type &e" + typeId +
						"&c is valid and a location was found."));
				return;
			}

			sender.sendMessage(GanglandChatUtil.commandMessage("&aCivilian &e" + typeId + "&a spawned near you."));
		}, sender -> {
			CivilianService service = gangland.getInitializer().getCivilianService();
			return new ArrayList<>(service.getCiviliansConfig().types().keySet());
		});

		this.addSubArgument(typeArg);
	}
}
