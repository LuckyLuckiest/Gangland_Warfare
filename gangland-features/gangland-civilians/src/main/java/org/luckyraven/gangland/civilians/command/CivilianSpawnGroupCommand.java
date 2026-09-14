package org.luckyraven.gangland.civilians.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;

class CivilianSpawnGroupCommand extends SubArgument {

	private final JavaPlugin        plugin;
	private final Tree<Argument>  tree;
	private final CivilianService civilianService;

	CivilianSpawnGroupCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                          CivilianService civilianService) {
		super(plugin, "spawngroup", tree, parent);

		this.plugin        = plugin;
		this.tree            = tree;
		this.civilianService = civilianService;

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

			String groupId = args[2];

			if (!civilianService.getCiviliansConfig().groups().containsKey(groupId)) {
				sender.sendMessage(Messages.CIVILIAN_GROUP_UNKNOWN.toString().replace("%group%", groupId));
				return;
			}

			civilianService.spawnGroup(player.getLocation(), groupId);

			sender.sendMessage(Messages.CIVILIAN_GROUP_SPAWNED.toString().replace("%group%", groupId));
		}, sender -> new ArrayList<>(civilianService.getCiviliansConfig().groups().keySet()));

		this.addSubArgument(groupArg);
	}
}
