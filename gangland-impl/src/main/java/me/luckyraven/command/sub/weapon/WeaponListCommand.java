package me.luckyraven.command.sub.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.configuration.WeaponAddon;
import org.bukkit.command.CommandSender;

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
