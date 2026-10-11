package org.luckyraven.gangland.fights.command.arena;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.ArrayList;
import java.util.List;

/** Test access to the protected tree builder of {@link ArenaCommand}. */
public final class ArenaTreeFixture {

	private ArenaTreeFixture() {
	}

	public static ArenaCommand command(JavaPlugin plugin) {
		ArenaCommand command = new ArenaCommand(plugin);
		command.initializeArguments();
		return command;
	}

	public static List<Argument> verbs(JavaPlugin plugin) {
		List<Argument> verbs = new ArrayList<>();
		for (Tree.Node<Argument> child : command(plugin).getArgument().getNode().getChildren()) {
			verbs.add(child.getData());
		}
		return verbs;
	}
}
