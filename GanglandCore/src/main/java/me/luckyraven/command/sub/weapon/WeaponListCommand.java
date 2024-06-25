package me.luckyraven.command.sub.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import org.bukkit.command.CommandSender;

import java.util.Iterator;
import java.util.Set;

class WeaponListCommand extends SubArgument {

	private final Gangland gangland;

	protected WeaponListCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super("list", tree, parent);

		this.gangland = gangland;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Set<String> weapons = gangland.getInitializer().getWeaponAddon().getWeaponKeys();

			sender.sendMessage(ChatUtil.commandMessage("List of weapons"));

			Iterator<String> iterator = weapons.iterator();
			StringBuilder    builder  = new StringBuilder();

			while (iterator.hasNext()) {
				Weapon weapon = gangland.getInitializer().getWeaponAddon().getWeapon(iterator.next());
				if (weapon == null) continue;

				builder.append("&b").append(weapon.getName());
				if (iterator.hasNext()) builder.append("&7, ");
			}

			sender.sendMessage(ChatUtil.color(builder.toString()));
		};
	}

}
