package org.luckyraven.gangland.command.sub.weapon;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.configuration.WeaponAddon;

import java.util.Iterator;
import java.util.Set;

class WeaponListCommand extends SubArgument {

	private final WeaponAddon weaponAddon;

	protected WeaponListCommand(Gangland gangland, Tree<Argument> tree, Argument parent, WeaponAddon weaponAddon) {
		super(gangland, "list", tree, parent);

		this.weaponAddon = weaponAddon;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Set<String> weapons = weaponAddon.getWeaponKeys();

			sender.sendMessage(Messages.WEAPON_LIST_HEADER.toString());

			Iterator<String> iterator = weapons.iterator();
			StringBuilder    builder  = new StringBuilder();

			while (iterator.hasNext()) {
				Weapon weapon = weaponAddon.getWeapon(iterator.next());
				if (weapon == null) continue;

				builder.append("&b").append(weapon.getName());
				if (iterator.hasNext()) builder.append("&7, ");
			}

			sender.sendMessage(GanglandChatUtil.color(builder.toString()));
		};
	}

}
