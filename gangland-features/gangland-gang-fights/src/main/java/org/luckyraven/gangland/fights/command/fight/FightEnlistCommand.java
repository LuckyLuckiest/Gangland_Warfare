package org.luckyraven.gangland.fights.command.fight;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.fights.fight.FightManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/** {@code /glw gang fight enlist}. */
public final class FightEnlistCommand extends SubArgument {

	private final @Nullable FightManager manager;

	public FightEnlistCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, @Nullable FightManager manager) {
		super(plugin, "enlist", tree, parent);
		this.manager = manager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return FightVerbs.run(() -> manager, player -> () -> manager.enlist(player.getUniqueId()));
	}
}
