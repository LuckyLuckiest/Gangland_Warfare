package me.luckyraven.command.sub.civilians;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;

class CivilianSpawnerSetCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;

	CivilianSpawnerSetCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "set", tree, parent);

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

			String          typeId  = args[3];
			CivilianService service = gangland.getInitializer().getCivilianService();

			if (!service.getCiviliansConfig().types().containsKey(typeId)) {
				sender.sendMessage(GanglandChatUtil.commandMessage("&cUnknown type &e" + typeId + "&c."));
				return;
			}

			CivilianSpawnManager spawnManager = gangland.getInitializer().getCivilianSpawnManager();
			spawnManager.setTypeSpawnerLocation(player.getLocation(), typeId);

			sender.sendMessage(GanglandChatUtil.commandMessage(
					"&aCivilian spawner &e" + typeId + "&a set at your location."));
		}, sender -> {
			CivilianService service = gangland.getInitializer().getCivilianService();
			return new ArrayList<>(service.getCiviliansConfig().types().keySet());
		});

		this.addSubArgument(typeArg);
	}
}
