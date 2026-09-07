package org.luckyraven.gangland.command.sub.weapon;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.gangland.weapon.ammo.Ammunition;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;

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

			sender.sendMessage(Messages.AMMO_LIST_HEADER.toString());

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
