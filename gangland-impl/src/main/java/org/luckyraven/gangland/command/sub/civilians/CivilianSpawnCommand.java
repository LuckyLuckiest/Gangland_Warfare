package org.luckyraven.gangland.command.sub.civilians;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.copsncrooks.npc.civilian.CivilianService;
import org.luckyraven.gangland.copsncrooks.npc.civilian.npc.CivilianNpc;
import org.luckyraven.gangland.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;

class CivilianSpawnCommand extends SubArgument {

	private final Gangland             gangland;
	private final Tree<Argument>       tree;
	private final CivilianService      civilianService;
	private final CivilianSpawnManager civilianSpawnManager;

	CivilianSpawnCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                     CivilianService civilianService, CivilianSpawnManager civilianSpawnManager) {
		super(gangland, "spawn", tree, parent);

		this.gangland             = gangland;
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
		Argument typeArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
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
