package org.luckyraven.gangland.turf.command;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfRules;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/**
 * {@code /glw turf rules}: the five sentences of how turf wars work on this server, with its numbers (W2-E1). The
 * body is a stub until W2-E1 fills it; the constructor is final.
 */
final class TurfRulesCommand extends SubArgument {

	private final TurfRules            rules;
	private final TurfSettings         settings;
	private final TurfMessages         text;
	private final TurfMessageContract  messages;

	TurfRulesCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, TurfRules rules, TurfSettings settings,
	                 TurfMessages text, TurfMessageContract messages) {
		super(plugin, "rules", tree, parent);
		this.rules    = rules;
		this.settings = settings;
		this.text     = text;
		this.messages = messages;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(text.text("Rules.Header", "&6How turf wars work"));
	}

}
