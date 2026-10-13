package org.luckyraven.gangland.fights.command.fight;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.fights.fight.FightManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/** Shared plumbing of the player verbs: the player check and the chained operand builder. */
final class FightVerbs {

	/** Index of the first operand: {@code /glw gang fight <verb> <operand...>} is args[0]=gang, [1]=fight, [2]=verb. */
	static final int FIRST_OPERAND = 3;

	private FightVerbs() {
	}

	/** The sending player, or null after telling a console that this is a player command. */
	static @Nullable Player player(CommandSender sender) {
		if (sender instanceof Player player) return player;

		sender.sendMessage(Messages.NOT_PLAYER.toString());
		return null;
	}

	/** Runs {@code verb} for a player sender; an unwired (tree-only) command does nothing. */
	static TriConsumer<Argument, CommandSender, String[]> run(Supplier<@Nullable FightManager> manager,
	                                                          Function<Player, Runnable> verb) {
		// the supplier is read at run time: the command's own field is still null while its super constructor runs
		return (argument, sender, args) -> {
			Player player = player(sender);
			if (player != null && manager.get() != null) verb.apply(player).run();
		};
	}

	static OptionalArgument operand(JavaPlugin plugin, Tree<Argument> tree, String label,
	                                TriConsumer<Argument, CommandSender, String[]> action,
	                                Supplier<List<String>> suggestions) {
		OptionalArgument operand = new OptionalArgument(plugin, tree, action, sender -> suggestions.get());
		operand.setDisplayName(label);
		return operand;
	}

	/** The number at {@code args[index]}, or null (after the "not a number" line) when it is not one. */
	static @Nullable Double number(Player player, String[] args, int index) {
		try {
			return Double.parseDouble(args[index]);
		} catch (NumberFormatException exception) {
			player.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", args[index]));
			return null;
		}
	}
}
