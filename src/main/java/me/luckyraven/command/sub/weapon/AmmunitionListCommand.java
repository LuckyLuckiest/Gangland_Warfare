package me.luckyraven.command.sub.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.ammo.Ammunition;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import org.bukkit.command.CommandSender;

import java.util.Set;

public class AmmunitionListCommand extends SubArgument {

	private final Gangland gangland;

	public AmmunitionListCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super("list", tree, parent);

		this.gangland = gangland;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Set<String> ammunitions = gangland.getInitializer().getAmmunitionAddon().getAmmunitionKeys();

			sender.sendMessage(ChatUtil.commandMessage("List of ammunition"));
			for (String ammunitionStr : ammunitions) {
				Ammunition ammunition = gangland.getInitializer().getAmmunitionAddon().getAmmunition(ammunitionStr);
				if (ammunition == null) continue;
				sender.sendMessage(ChatUtil.color(ammunition.getDisplayName()));
			}
		};
	}

}
