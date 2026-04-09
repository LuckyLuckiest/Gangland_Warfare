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

class CivilianSpawnerSetGroupCommand extends SubArgument {

	private final Gangland             gangland;
	private final Tree<Argument>       tree;
	private final CivilianService      civilianService;
	private final CivilianSpawnManager civilianSpawnManager;

	CivilianSpawnerSetGroupCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                               CivilianService civilianService, CivilianSpawnManager civilianSpawnManager) {
		super(gangland, "setgroup", tree, parent);

		this.gangland             = gangland;
		this.tree                 = tree;
		this.civilianService      = civilianService;
		this.civilianSpawnManager = civilianSpawnManager;

		groupIdArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<groupId>"));
		};
	}

	private void groupIdArgument() {
		Argument groupArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return;
			}

			String groupId = args[3];

			if (!civilianService.getCiviliansConfig().groups().containsKey(groupId)) {
				sender.sendMessage(GanglandChatUtil.commandMessage("&cUnknown group &e" + groupId + "&c."));
				return;
			}

			civilianSpawnManager.setGroupSpawnerLocation(player.getLocation(), groupId);

			sender.sendMessage(GanglandChatUtil.commandMessage(
					"&aGroup spawner &e" + groupId + "&a set at your location."));
		}, sender -> new ArrayList<>(civilianService.getCiviliansConfig().groups().keySet()));

		this.addSubArgument(groupArg);
	}
}
