package org.luckyraven.gangland.command.sub.gang.ally;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.combat.FriendlyFirePolicy;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/**
 * {@code /glw gang ally pvp <gang> <on|off>}: lets your gang and an ally hurt each other (both sides must say yes).
 * Stub: lane W5-F fills the body; until then Keystone answers "not implemented yet".
 */
final class GangAllyPvpCommand extends SubArgument {

	private final GangManager        gangManager;
	private final FriendlyFirePolicy friendlyFire;

	GangAllyPvpCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, GangManager gangManager,
	                   FriendlyFirePolicy friendlyFire) {
		super(gangland, "pvp", tree, parent);

		this.gangManager  = gangManager;
		this.friendlyFire = friendlyFire;

		OptionalArgument ally = new OptionalArgument(gangland, tree);
		ally.setDisplayName("gang");
		OptionalArgument state = new OptionalArgument(gangland, tree);
		state.setDisplayName("on|off");
		ally.addSubArgument(state);
		this.addSubArgument(ally);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return null;
	}
}
