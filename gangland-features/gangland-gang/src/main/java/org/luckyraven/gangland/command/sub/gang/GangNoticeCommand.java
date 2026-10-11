package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/**
 * {@code /glw gang notice <text|clear>}: the notice members see when they log in. Stub: lane W5-U2 fills the body;
 * until then Keystone answers "not implemented yet".
 */
final class GangNoticeCommand extends SubArgument {

	GangNoticeCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "notice", tree, parent);

		OptionalArgument text = new OptionalArgument(gangland, tree);
		text.setDisplayName("text|clear");
		// greedy, so every word after "notice" reaches the action
		text.setGreedy(true);
		this.addSubArgument(text);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return null;
	}
}
