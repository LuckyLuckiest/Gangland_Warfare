package me.luckyraven.command.sub.civilians.spawner;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class CivilianSpawnerTeleportCommand extends SubArgument {

	private final Gangland             gangland;
	private final Tree<Argument>       tree;
	private final CivilianSpawnManager civilianSpawnManager;

	CivilianSpawnerTeleportCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                               CivilianSpawnManager civilianSpawnManager) {
		super(gangland, new String[]{"teleport", "tp"}, tree, parent);

		this.gangland             = gangland;
		this.tree                 = tree;
		this.civilianSpawnManager = civilianSpawnManager;

		this.idArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<id>"));
		};
	}

	private void idArgument() {
		Argument idArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return;
			}

			String idStr = args[3];
			int    id;
			try {
				id = Integer.parseInt(idStr);
			} catch (NumberFormatException e) {
				sender.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", idStr));
				return;
			}

			Location location = civilianSpawnManager.getSpawnerLocation(id);

			if (location == null) {
				sender.sendMessage(Messages.LOCATION_NOT_FOUND.toString().replace("%location%", idStr));
				return;
			}

			player.teleport(location);
			sender.sendMessage(Messages.CIVILIAN_SPAWNER_TELEPORTED.toString().replace("%id%", String.valueOf(id)));
		}, sender -> civilianSpawnManager.getSpawnerIds()
				.stream().map(String::valueOf).toList());

		this.addSubArgument(idArg);
	}
}
