package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.safehouse.GangSafehouseService;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/**
 * {@code /glw gang rally [safehouse]} and {@code /glw gang rally join}. Stub: lane W5-H fills the bodies; until then
 * Keystone answers "not implemented yet".
 */
final class GangRallyCommand extends SubArgument {

	private final GangSafehouseService safehouseService;

	GangRallyCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                 GangSafehouseService safehouseService) {
		super(gangland, "rally", tree, parent);

		this.safehouseService = safehouseService;

		this.addSubArgument(new StubJoin(gangland, tree, this));

		OptionalArgument safehouse = new OptionalArgument(gangland, tree);
		safehouse.setDisplayName("safehouse");
		this.addSubArgument(safehouse);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return null;
	}

	/** {@code join} with no body yet. */
	private static final class StubJoin extends SubArgument {

		StubJoin(JavaPlugin gangland, Tree<Argument> tree, Argument parent) {
			super(gangland, "join", tree, parent);
		}

		@Override
		protected TriConsumer<Argument, CommandSender, String[]> action() {
			return null;
		}
	}
}
