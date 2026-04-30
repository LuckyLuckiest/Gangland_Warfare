package org.luckyraven.gangland.command.sub.civilians.spawner;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.copsncrooks.npc.civilian.CivilianService;
import org.luckyraven.gangland.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

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
				sender.sendMessage(Messages.CIVILIAN_GROUP_UNKNOWN.toString().replace("%group%", groupId));
				return;
			}

			civilianSpawnManager.setGroupSpawnerLocation(player.getLocation(), groupId);

			sender.sendMessage(Messages.CIVILIAN_SPAWNER_GROUP_SET.toString().replace("%group%", groupId));
		}, sender -> new ArrayList<>(civilianService.getCiviliansConfig().groups().keySet()));

		this.addSubArgument(groupArg);
	}
}
