package me.luckyraven.command.sub.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.datastructure.JsonFormatter;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

class WeaponInfoCommand extends SubArgument {

	private final Gangland gangland;

	protected WeaponInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "info", tree, parent);

		this.gangland = gangland;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player player = (Player) sender;
			// get the held item
			ItemStack itemStack = player.getInventory().getItemInMainHand();
			// validate and get the weapon
			Weapon weapon = gangland.getInitializer().getWeaponManager().validateAndGetWeapon(player, itemStack);

			if (weapon == null) {
				player.sendMessage(ChatUtil.prefixMessage("Not weapon!"));
				return;
			}

			// display the necessary information
			JsonFormatter jsonFormatter = new JsonFormatter();

			player.sendMessage(jsonFormatter.formatToJson(weapon.toString(), " ".repeat(3)));
		};
	}

}
