package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.GangWorldPolicy;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.safehouse.GangSafehouseService;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/**
 * {@code /glw gang safehouse [name]} with {@code set <name>}, {@code remove <name>} and {@code list}. Stub: lane W5-H
 * fills the bodies; until then Keystone answers "not implemented yet".
 */
final class GangSafehouseCommand extends SubArgument {

	private final GangSafehouseService safehouseService;
	private final GangLimits           limits;
	private final GangWorldPolicy      worldPolicy;

	GangSafehouseCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                     GangSafehouseService safehouseService, GangLimits limits, GangWorldPolicy worldPolicy) {
		super(gangland, "safehouse", tree, parent);

		this.safehouseService = safehouseService;
		this.limits           = limits;
		this.worldPolicy      = worldPolicy;

		OptionalArgument name = new OptionalArgument(gangland, tree);
		name.setDisplayName("name");
		this.addSubArgument(name);

		this.addSubArgument(verb(gangland, tree, "set", true));
		this.addSubArgument(verb(gangland, tree, "remove", true));
		this.addSubArgument(verb(gangland, tree, "list", false));
	}

	private Argument verb(JavaPlugin gangland, Tree<Argument> tree, String label, boolean withName) {
		Argument verb = new StubVerb(gangland, label, tree, this);
		if (withName) {
			OptionalArgument name = new OptionalArgument(gangland, tree);
			name.setDisplayName("name");
			verb.addSubArgument(name);
		}
		return verb;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return null;
	}

	/** A child verb with no body yet. */
	private static final class StubVerb extends SubArgument {

		StubVerb(JavaPlugin gangland, String label, Tree<Argument> tree, Argument parent) {
			super(gangland, label, tree, parent);
		}

		@Override
		protected TriConsumer<Argument, CommandSender, String[]> action() {
			return null;
		}
	}
}
