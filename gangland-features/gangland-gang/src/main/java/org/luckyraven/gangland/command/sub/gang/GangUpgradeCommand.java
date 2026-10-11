package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.level.GangLevelTable;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.ConfirmArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/**
 * {@code /glw gang upgrade [confirm]}: shows what the next gang level costs, and claims it. Stub: lane W5-L fills the
 * bodies; until then Keystone answers "not implemented yet".
 */
final class GangUpgradeCommand extends SubArgument {

	private final GangLimits     limits;
	private final GangLevelTable levelTable;
	private final GangManager    gangManager;

	GangUpgradeCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, GangLimits limits,
	                   GangLevelTable levelTable, GangManager gangManager) {
		super(gangland, "upgrade", tree, parent);

		this.limits      = limits;
		this.levelTable  = levelTable;
		this.gangManager = gangManager;

		this.addSubArgument(new ConfirmArgument(gangland, tree));
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return null;
	}
}
