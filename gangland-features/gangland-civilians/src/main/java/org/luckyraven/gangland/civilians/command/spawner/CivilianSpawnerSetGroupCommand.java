package org.luckyraven.gangland.civilians.command.spawner;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;

class CivilianSpawnerSetGroupCommand extends SubArgument {

	private final JavaPlugin             plugin;
	private final Tree<Argument>       tree;
	private final CivilianService      civilianService;
	private final CivilianSpawnManager civilianSpawnManager;

	CivilianSpawnerSetGroupCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                               CivilianService civilianService, CivilianSpawnManager civilianSpawnManager) {
		super(plugin, "setgroup", tree, parent);

		this.plugin             = plugin;
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
		Argument groupArg = new OptionalArgument(plugin, tree, (argument, sender, args) -> {
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
