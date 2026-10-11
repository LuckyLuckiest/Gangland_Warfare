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
 * {@code /glw gang ranking [stat] [page]}: the gang ranking. Stub: lane W5-S fills the body; until then Keystone
 * answers "not implemented yet".
 */
final class GangRankingCommand extends SubArgument {

	private final GangStatsService statsService;

	GangRankingCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, GangStatsService statsService) {
		super(gangland, "ranking", tree, parent);

		this.statsService = statsService;

		OptionalArgument stat = new OptionalArgument(gangland, tree);
		stat.setDisplayName("stat");
		OptionalArgument page = new OptionalArgument(gangland, tree);
		page.setDisplayName("page");
		stat.addSubArgument(page);
		this.addSubArgument(stat);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return null;
	}
}
