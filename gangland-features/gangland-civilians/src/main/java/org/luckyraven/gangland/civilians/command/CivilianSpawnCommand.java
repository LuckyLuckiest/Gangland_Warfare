package org.luckyraven.gangland.civilians.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;

class CivilianSpawnCommand extends SubArgument {

	private final JavaPlugin             plugin;
	private final Tree<Argument>       tree;
	private final CivilianService      civilianService;
	private final CivilianSpawnManager civilianSpawnManager;

	CivilianSpawnCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                     CivilianService civilianService, CivilianSpawnManager civilianSpawnManager) {
		super(plugin, "spawn", tree, parent);

		this.plugin             = plugin;
		this.tree                 = tree;
		this.civilianService      = civilianService;
		this.civilianSpawnManager = civilianSpawnManager;

		typeIdArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<typeId>"));
		};
	}

	private void typeIdArgument() {
		Argument typeArg = new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return;
			}

			String      typeId = args[2];
			CivilianNpc npc    = civilianSpawnManager.spawnNearLocation(player, typeId);

			if (npc == null) {
				sender.sendMessage(Messages.CIVILIAN_SPAWN_FAILED.toString().replace("%type%", typeId));
				return;
			}

			sender.sendMessage(Messages.CIVILIAN_SPAWNED.toString().replace("%type%", typeId));
		}, sender -> new ArrayList<>(civilianService.getCiviliansConfig().types().keySet()));

		this.addSubArgument(typeArg);
	}
}
