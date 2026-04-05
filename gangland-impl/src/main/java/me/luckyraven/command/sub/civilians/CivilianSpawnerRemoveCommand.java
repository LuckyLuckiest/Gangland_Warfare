package me.luckyraven.command.sub.civilians;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;

class CivilianSpawnerRemoveCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;

	CivilianSpawnerRemoveCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "remove", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

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

			CivilianSpawnManager spawnManager = gangland.getInitializer().getCivilianSpawnManager();
			Location             location     = spawnManager.getSpawnerLocation(id);
			if (location == null) {
				sender.sendMessage(Messages.LOCATION_NOT_FOUND.toString().replace("%location%", idStr));
				return;
			}

			spawnManager.removeSpawner(id);

			sender.sendMessage(GanglandChatUtil.commandMessage("&aCivilian spawner &e" + id + "&a removed."));
		}, sender -> gangland.getInitializer().getCivilianSpawnManager().getSpawnerIds()
				.stream().map(String::valueOf).toList());

		this.addSubArgument(idArg);
	}
}
