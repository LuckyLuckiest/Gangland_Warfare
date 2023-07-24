package me.luckyraven.command.sub.gang;

import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.TriConsumer;
import me.luckyraven.datastructure.Tree;
import org.bukkit.command.CommandSender;

class GangInviteCommand extends SubArgument {

	GangInviteCommand(Tree<Argument> tree) {
		super(new String[]{"invite", "add"}, tree);

		setPermission(getPermission() + ".add_user");

		// TODO 
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return null;
	}

}
