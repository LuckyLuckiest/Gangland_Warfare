package me.luckyraven.command.sub.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.TriConsumer;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.datastructure.JsonFormatter;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.feature.weapon.ammo.Ammunition;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

class AmmunitionInfoCommand extends SubArgument {

	private final Gangland gangland;

	protected AmmunitionInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "info", tree, parent);

		this.gangland = gangland;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player player = (Player) sender;
			// get the held item
			ItemStack itemStack = player.getInventory().getItemInMainHand();

			// check if it was an ammunition
			if (!Ammunition.isAmmunition(itemStack)) {
				player.sendMessage(ChatUtil.prefixMessage("Not ammunition!"));
				return;
			}

			// get the ammunition as the held item
			Ammunition ammunition = Ammunition.getHeldAmmunition(gangland, itemStack);

			// display the necessary information
			JsonFormatter jsonFormatter = new JsonFormatter();

			player.sendMessage(jsonFormatter.formatToJson(ChatUtil.color(ammunition.toString()), " ".repeat(3)));
		};
	}

}
