package me.luckyraven.command.sub.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.JsonFormatter;
import me.luckyraven.util.datastructure.Tree;
import me.luckyraven.weapon.Weapon;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

class WeaponInfoCommand extends SubArgument {

	private final Gangland            gangland;
	private final UserManager<Player> userManager;

	protected WeaponInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "info", tree, parent);

		this.gangland    = gangland;
		this.userManager = gangland.getInitializer().getUserManager();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			// get the held item
			ItemStack itemStack = player.getInventory().getItemInMainHand();
			// validate and get the weapon
			Weapon weapon = gangland.getInitializer().getWeaponManager().validateAndGetWeapon(player, itemStack);

			if (weapon == null) {
				user.sendMessage(GanglandChatUtil.prefixMessage("Not weapon!"));
				return;
			}

			// display the necessary information
			JsonFormatter jsonFormatter = new JsonFormatter();

			user.sendMessage(jsonFormatter.formatToJson(GanglandChatUtil.color(weapon.toString()), " ".repeat(3)));
		};
	}

}
