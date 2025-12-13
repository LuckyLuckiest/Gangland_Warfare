package me.luckyraven.command.sub.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.JsonFormatter;
import me.luckyraven.util.datastructure.Tree;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.configuration.AmmunitionAddon;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

class AmmunitionInfoCommand extends SubArgument {

	private final Gangland            gangland;
	private final UserManager<Player> userManager;

	protected AmmunitionInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "info", tree, parent);

		this.gangland    = gangland;
		this.userManager = gangland.getInitializer().getUserManager();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);
			// get the held item
			ItemStack itemStack = player.getInventory().getItemInMainHand();

			// check if it was an ammunition
			if (!Ammunition.isAmmunition(itemStack)) {
				user.sendMessage(ChatUtil.prefixMessage("Not ammunition!"));
				return;
			}

			// get the ammunition as the held item
			AmmunitionAddon ammunitionAddon = gangland.getInitializer().getAmmunitionAddon();
			Ammunition      ammunition      = Ammunition.getHeldAmmunition(ammunitionAddon, itemStack);

			// display the necessary information
			JsonFormatter jsonFormatter = new JsonFormatter();

			user.sendMessage(jsonFormatter.formatToJson(ChatUtil.color(ammunition.toString()), " ".repeat(3)));
		};
	}

}
