package org.luckyraven.gangland.turf.command;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;

class TurfPos2Command extends SubArgument {

	private final WandSelectionManager selections;
	private final TurfMessages         text;

	protected TurfPos2Command(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                          WandSelectionManager selections,
	                          TurfMessages text) {
		super(plugin, "pos2", tree, parent);

		this.selections = selections;
		this.text       = text;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> TurfPos1Command.setCorner(sender, selections, text, false, "pos2");
	}
}
