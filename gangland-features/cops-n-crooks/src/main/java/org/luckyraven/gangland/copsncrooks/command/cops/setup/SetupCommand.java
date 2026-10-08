package org.luckyraven.gangland.copsncrooks.command.cops.setup;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.setup.SetupCommands;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * {@code /glw cop setup wand|mode <mode>|save <name...>|list [kind]|remove <kind> <id>|tp <kind> <id>|link <stationId>
 * <jailId|none>}. A thin argument tree over {@link SetupCommands}. Nested argument permissions only add onto the base
 * {@code cop} node, so every action gates on {@code SetupSelections.PERMISSION} itself (the turf wand's GI-35 rule).
 * A missing operand sends the full usage of that node, so its command is coloured by {@code commandDesign}.
 *
 * @since 0.16.0
 */
public final class SetupCommand extends SubArgument {

	/** The root has no {@code cop_setup_help} entry, so a bare {@code /glw cop setup} sends this usage. */
	private static final String ROOT_USAGE = "/glw cop setup <wand|mode|save|list|remove|tp|link>";

	private final JavaPlugin     plugin;
	private final Tree<Argument> tree;
	private final SetupCommands  commands;

	public SetupCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, SetupCommands commands) {
		super(plugin, "setup", tree, parent);

		this.plugin   = plugin;
		this.tree     = tree;
		this.commands = commands;

		initializeArguments();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (commands.permitted(sender)) commands.usage(sender, ROOT_USAGE);
		};
	}

	// args = [cop, setup, <sub>, <operands...>]: the operands start at index 3
	private void initializeArguments() {
		Argument wand = node("wand", (argument, sender, args) -> {
			Player admin = commands.admin(sender);
			if (admin != null) commands.give(admin);
		});

		Argument mode = node("mode", usage("/glw cop setup mode <mode>"));
		mode.addSubArgument(free("mode", commands::modes, (argument, sender, args) -> {
			Player admin = commands.admin(sender);
			if (admin != null) commands.mode(admin, args[3]);
		}));

		Argument         save = node("save", usage("/glw cop setup save <name...>"));
		OptionalArgument name = free("name", List::of, (argument, sender, args) -> {
			Player admin = commands.admin(sender);
			if (admin != null) commands.save(admin, String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
		});
		name.setGreedy(true);
		save.addSubArgument(name);

		Argument list = node("list", (argument, sender, args) -> {
			if (commands.permitted(sender)) commands.list(sender, null);
		});
		list.addSubArgument(free("kind", commands::kinds, (argument, sender, args) -> {
			if (commands.permitted(sender)) commands.list(sender, args[3]);
		}));

		Argument remove = kindThenId("remove", "/glw cop setup remove <kind> <id>", (argument, sender, args) -> {
			if (commands.permitted(sender)) commands.remove(sender, args[3], args[4]);
		});
		Argument tp = kindThenId("tp", "/glw cop setup tp <kind> <id>", (argument, sender, args) -> {
			Player admin = commands.admin(sender);
			if (admin != null) commands.teleport(admin, args[3], args[4]);
		});

		String           linkUsage = "/glw cop setup link <stationId> <jailId|none>";
		Argument         link      = node("link", usage(linkUsage));
		OptionalArgument station   = free("stationId", commands::stationIds, usage(linkUsage));
		station.addSubArgument(free("jailId", commands::jailChoices, (argument, sender, args) -> {
			if (commands.permitted(sender)) commands.link(sender, args[3], args[4]);
		}));
		link.addSubArgument(station);

		this.addAllSubArguments(List.of(wand, mode, save, list, remove, tp, link));
	}

	/** {@code <word> <kind> <id>}: the kind suggests station/region/point, the id every id in the registries. */
	private Argument kindThenId(String word, String fullUsage, TriConsumer<Argument, CommandSender, String[]> action) {
		Argument         root = node(word, usage(fullUsage));
		OptionalArgument kind = free("kind", commands::kinds, usage(fullUsage));
		kind.addSubArgument(free("id", commands::ids, action));
		root.addSubArgument(kind);
		return root;
	}

	/** Sends the full {@code /glw ...} usage of this node to a sender who may use the tools. */
	private TriConsumer<Argument, CommandSender, String[]> usage(String fullUsage) {
		return (argument, sender, args) -> {
			if (commands.permitted(sender)) commands.usage(sender, fullUsage);
		};
	}

	private Argument node(String word, TriConsumer<Argument, CommandSender, String[]> action) {
		return new Argument(plugin, word, tree, action);
	}

	/** One free-text operand with a tab-completion source and a usage label. */
	private OptionalArgument free(String label, Supplier<List<String>> suggestions,
	                              TriConsumer<Argument, CommandSender, String[]> action) {
		OptionalArgument argument = new OptionalArgument(plugin, tree, action, sender -> suggestions.get());
		argument.setDisplayName(label);
		return argument;
	}
}
