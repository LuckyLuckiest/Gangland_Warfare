package me.luckyraven.command.sub.cops;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

class CopSpawnerCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;

	CopSpawnerCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "spawner", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		initializeArguments();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			String message = GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(),
			                                               "<set/remove/list/info/teleport>");
			sender.sendMessage(message);
		};
	}

	private void initializeArguments() {
		Argument setArg      = new CopSpawnerSetCommand(gangland, tree, this);
		Argument removeArg   = new CopSpawnerRemoveCommand(gangland, tree, this);
		Argument listArg     = new CopSpawnerListCommand(gangland, tree, this);
		Argument infoArg     = new CopSpawnerInfoCommand(gangland, tree, this);
		Argument teleportArg = new CopSpawnerTeleportCommand(gangland, tree, this);

		List<Argument> arguments = new ArrayList<>();

		arguments.add(setArg);
		arguments.add(removeArg);
		arguments.add(listArg);
		arguments.add(infoArg);
		arguments.add(teleportArg);

		this.addAllSubArguments(arguments);
	}
}
