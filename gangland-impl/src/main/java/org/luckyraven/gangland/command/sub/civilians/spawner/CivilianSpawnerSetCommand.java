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

class CivilianSpawnerSetCommand extends SubArgument {

	private final Gangland             gangland;
	private final Tree<Argument>       tree;
	private final CivilianService      civilianService;
	private final CivilianSpawnManager civilianSpawnManager;

	CivilianSpawnerSetCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                          CivilianService civilianService, CivilianSpawnManager civilianSpawnManager) {
		super(gangland, "set", tree, parent);

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

			String typeId = args[3];

			if (!civilianService.getCiviliansConfig().types().containsKey(typeId)) {
				sender.sendMessage(Messages.CIVILIAN_TYPE_UNKNOWN.toString().replace("%type%", typeId));
				return;
			}

			civilianSpawnManager.setTypeSpawnerLocation(player.getLocation(), typeId);

			sender.sendMessage(Messages.CIVILIAN_SPAWNER_TYPE_SET.toString().replace("%type%", typeId));
		}, sender -> new ArrayList<>(civilianService.getCiviliansConfig().types().keySet()));

		this.addSubArgument(typeArg);
	}
}
