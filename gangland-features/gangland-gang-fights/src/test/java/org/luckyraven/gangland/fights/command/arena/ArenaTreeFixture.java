package org.luckyraven.gangland.fights.command.arena;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.fights.FightRules;
import org.luckyraven.gangland.fights.arena.ArenaDrafts;
import org.luckyraven.gangland.fights.arena.ArenaMessages;
import org.luckyraven.gangland.fights.arena.ArenaRegistry;
import org.mockito.Mockito;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.ArrayList;
import java.util.List;

/** Test access to the protected tree builder of {@link ArenaCommand}. */
public final class ArenaTreeFixture {

	private ArenaTreeFixture() {
	}

	public static ArenaCommand command(JavaPlugin plugin) {
		ArenaCommand command = new ArenaCommand(plugin, new ArenaRegistry(), new ArenaDrafts(), Mockito.mock(ArenaMessages.class),
		                                        Mockito.mock(FightRules.class));
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
