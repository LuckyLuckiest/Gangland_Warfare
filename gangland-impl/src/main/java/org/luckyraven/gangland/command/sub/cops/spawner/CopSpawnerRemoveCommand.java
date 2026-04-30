package org.luckyraven.gangland.command.sub.cops.spawner;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

class CopSpawnerRemoveCommand extends SubArgument {

	private final Gangland        gangland;
	private final Tree<Argument>  tree;
	private final CopSpawnManager copSpawnManager;

	CopSpawnerRemoveCommand(Gangland gangland, Tree<Argument> tree, Argument parent, CopSpawnManager copSpawnManager) {
		super(gangland, "remove", tree, parent);

		this.gangland        = gangland;
		this.tree            = tree;
		this.copSpawnManager = copSpawnManager;

		idArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<id>"));
		};
	}

	private void idArgument() {
		Argument idArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			String idStr = args[3];
			int    id;
			try {
				id = Integer.parseInt(idStr);
			} catch (NumberFormatException e) {
				sender.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", idStr));
				return;
			}

			Location location = copSpawnManager.getSpawnerLocation(id);
			if (location == null) {
				sender.sendMessage(Messages.LOCATION_NOT_FOUND.toString().replace("%location%", idStr));
				return;
			}

			copSpawnManager.removeSpawner(id);

			sender.sendMessage(Messages.COP_SPAWNER_REMOVED.toString().replace("%id%", String.valueOf(id)));
		}, sender -> copSpawnManager.getSpawnerIds()
				.stream().map(String::valueOf).toList());

		this.addSubArgument(idArg);
	}
}
