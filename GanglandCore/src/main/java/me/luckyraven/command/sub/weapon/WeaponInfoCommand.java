package me.luckyraven.command.sub.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.util.TriConsumer;
import org.bukkit.command.CommandSender;

class WeaponInfoCommand extends SubArgument {

	private final Gangland gangland;

	protected WeaponInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "info", tree, parent);

		this.gangland = gangland;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {

		};
	}

}
