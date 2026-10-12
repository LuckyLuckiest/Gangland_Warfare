package org.luckyraven.gangland.fights.command.fight;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.fights.fight.FightManager;
import org.luckyraven.gangland.command.extension.CommandContribution;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.List;

/** Attaches {@code fight} under the gang module's {@code /glw gang}. */
public final class FightContribution implements CommandContribution {

	public static final String PARENT = "gang";

	private final JavaPlugin plugin;
	private final @Nullable FightManager manager;

	/** Tree only: the verbs show up in help and completion but do nothing. For tests. */
	public FightContribution(JavaPlugin plugin) {
		this(plugin, null);
	}

	public FightContribution(JavaPlugin plugin, @Nullable FightManager manager) {
		this.plugin  = plugin;
		this.manager = manager;
	}

	@Override
	public String parent() {
		return PARENT;
	}

	@Override
	public List<Argument> create(Tree<Argument> tree, Argument parent) {
		return List.of(new FightCommand(plugin, tree, parent, manager));
	}
}
