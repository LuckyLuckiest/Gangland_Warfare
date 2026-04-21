package me.luckyraven.command.sub.civilians;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
