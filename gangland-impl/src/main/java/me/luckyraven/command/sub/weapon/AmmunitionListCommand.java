package me.luckyraven.command.sub.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.configuration.AmmunitionAddon;
import org.bukkit.command.CommandSender;

import java.util.Iterator;
import java.util.Set;

class AmmunitionListCommand extends SubArgument {

	private final Gangland gangland;

	protected AmmunitionListCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "list", tree, parent);

		this.gangland = gangland;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			AmmunitionAddon ammunitionAddon = gangland.getInitializer().getAmmunitionAddon();
			Set<String>     ammunition      = ammunitionAddon.getAmmunitionKeys();

			sender.sendMessage(ChatUtil.commandMessage("List of ammunition"));

			Iterator<String> iterator = ammunition.iterator();
			StringBuilder    builder  = new StringBuilder();

			while (iterator.hasNext()) {
				Ammunition ammo = ammunitionAddon.getAmmunition(iterator.next());
				if (ammo == null) continue;

				builder.append("&b").append(ammo.getName());
				if (iterator.hasNext()) builder.append("&7, ");
			}

			sender.sendMessage(ChatUtil.color(builder.toString()));
		};
	}

}
