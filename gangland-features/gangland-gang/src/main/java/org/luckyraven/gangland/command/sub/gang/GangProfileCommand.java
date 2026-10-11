package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/**
 * {@code /glw gang profile [player]}: a player's gang card. Stub: lane W5-S fills the body; until then Keystone answers
 * "not implemented yet".
 */
final class GangProfileCommand extends SubArgument {

	private final GangStatsService statsService;

	GangProfileCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, GangStatsService statsService) {
		super(gangland, "profile", tree, parent);

		this.statsService = statsService;

		OptionalArgument player = new OptionalArgument(gangland, tree);
		player.setDisplayName("player");
		this.addSubArgument(player);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return null;
	}
}
