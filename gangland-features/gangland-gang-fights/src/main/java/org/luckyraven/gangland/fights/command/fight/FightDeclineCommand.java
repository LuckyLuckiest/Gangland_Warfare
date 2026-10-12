package org.luckyraven.gangland.fights.command.fight;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.fights.fight.FightManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.List;

/** {@code /glw gang fight decline [gang]}: turns down the challenge of that gang, or the only one waiting. */
public final class FightDeclineCommand extends SubArgument {

	private final @Nullable FightManager manager;

	public FightDeclineCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, @Nullable FightManager manager) {
		super(plugin, "decline", tree, parent);
		this.manager = manager;

		this.addSubArgument(FightVerbs.operand(plugin, tree, "gang", (argument, sender, args) -> decline(sender, args),
		                                       () -> manager == null ? List.of() : manager.gangNames()));
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> decline(sender, args);
	}

	private void decline(CommandSender sender, String[] args) {
		Player player = FightVerbs.player(sender);
		if (player == null || manager == null) return;

		manager.decline(player.getUniqueId(), args.length > FightVerbs.FIRST_OPERAND ? args[FightVerbs.FIRST_OPERAND] : null);
	}
}
