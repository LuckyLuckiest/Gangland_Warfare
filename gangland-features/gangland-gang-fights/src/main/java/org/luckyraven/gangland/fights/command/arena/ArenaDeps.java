package org.luckyraven.gangland.fights.command.arena;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.fights.FightRules;
import org.luckyraven.gangland.fights.arena.ArenaDrafts;
import org.luckyraven.gangland.fights.arena.ArenaMessages;
import org.luckyraven.gangland.fights.arena.ArenaPoint;
import org.luckyraven.gangland.fights.arena.ArenaRegistry;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.List;

/** What every arena verb shares, plus the few helpers they all use. */
record ArenaDeps(ArenaRegistry registry, ArenaDrafts drafts, ArenaMessages text, FightRules rules) {

	static final String NAME_PATTERN = "[A-Za-z0-9_-]{1,24}";

	/** The sender as a player, or null after telling a console it cannot build arenas. */
	Player player(CommandSender sender) {
		if (sender instanceof Player player) {
			return player;
		}
		sender.sendMessage(text.text("Arenas.Players_Only"));
		return null;
	}

	static ArenaPoint point(Location at) {
		return new ArenaPoint(at.getWorld() == null ? "" : at.getWorld().getName(), at.getX(), at.getY(), at.getZ(),
		                      at.getYaw(), at.getPitch());
	}

	/** One chained operand: {@code action} gets the operand text (args[2]) and the sender. */
	OptionalArgument operand(JavaPlugin plugin, Tree<Argument> tree, String label, List<String> fixed,
	                         java.util.function.BiConsumer<CommandSender, String> action) {
		TriConsumer<Argument, CommandSender, String[]> run = (argument, sender, args) -> action.accept(sender, args[2]);
		OptionalArgument operand = new OptionalArgument(plugin, tree, run, sender -> fixed != null ? fixed : names());
		operand.setDisplayName(label);
		return operand;
	}

	List<String> names() {
		return registry.all().stream().map(a -> a.name()).toList();
	}
}
