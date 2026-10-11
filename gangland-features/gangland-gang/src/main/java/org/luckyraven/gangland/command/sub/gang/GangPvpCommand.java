package org.luckyraven.gangland.command.sub.gang;

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
 * {@code /glw gang pvp <on|off>}: lets the gang's members hurt each other, or stops it. Stub: lane W5-F fills the
 * body; until then Keystone answers "not implemented yet".
 */
final class GangPvpCommand extends SubArgument {

	private final GangManager        gangManager;
	private final FriendlyFirePolicy friendlyFire;

	GangPvpCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, GangManager gangManager,
	               FriendlyFirePolicy friendlyFire) {
		super(gangland, "pvp", tree, parent);

		this.gangManager  = gangManager;
		this.friendlyFire = friendlyFire;

		OptionalArgument state = new OptionalArgument(gangland, tree);
		state.setDisplayName("on|off");
		this.addSubArgument(state);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return null;
	}
}
