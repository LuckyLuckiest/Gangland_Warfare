package me.luckyraven.command.sub.copsncrooks;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.police.spawn.CopSpawnManager;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

class CopSpawnerRemoveCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;

	CopSpawnerRemoveCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "remove", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		idArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<id>"));
	}

	private void idArgument() {
		Argument idArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			String idStr = args[2];
			int    id;
			try {
				id = Integer.parseInt(idStr);
			} catch (NumberFormatException e) {
				sender.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString().replace("%command%", idStr));
				return;
			}

			CopSpawnManager copSpawnManager = gangland.getInitializer().getCopSpawnManager();
			copSpawnManager.removeSpawner(id);

			sender.sendMessage(ChatUtil.commandMessage("&aCop spawner &e" + id + "&a removed."));
		}, sender -> gangland.getInitializer().getCopSpawnManager().getSpawnerIds()
				.stream().map(String::valueOf).toList());

		this.addSubArgument(idArg);
	}
}
