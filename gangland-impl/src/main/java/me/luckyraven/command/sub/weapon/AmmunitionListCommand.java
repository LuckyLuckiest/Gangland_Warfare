package me.luckyraven.command.sub.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import org.bukkit.command.CommandSender;

import java.util.Iterator;
import java.util.Set;

class AmmunitionListCommand extends SubArgument {

	private final AmmunitionManager ammunitionManager;

	protected AmmunitionListCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                                AmmunitionManager ammunitionManager) {
		super(gangland, "list", tree, parent);

		this.ammunitionManager = ammunitionManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Set<String> ammunition = ammunitionManager.getAmmunitionKeys();

			sender.sendMessage(GanglandChatUtil.commandMessage("List of ammunition"));

			Iterator<String> iterator = ammunition.iterator();
			StringBuilder    builder  = new StringBuilder();

			while (iterator.hasNext()) {
				Ammunition ammo = ammunitionManager.getAmmunition(iterator.next());
				if (ammo == null) continue;

				builder.append("&b").append(ammo.getName());
				if (iterator.hasNext()) builder.append("&7, ");
			}

			sender.sendMessage(GanglandChatUtil.color(builder.toString()));
		};
	}

}
