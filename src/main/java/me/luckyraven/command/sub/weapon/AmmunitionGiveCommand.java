package me.luckyraven.command.sub.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.weapon.WeaponAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class AmmunitionGiveCommand extends SubArgument {

	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	public AmmunitionGiveCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super("give", tree, parent);

		this.tree = tree;
		this.userManager = gangland.getInitializer().getUserManager();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
	}

	private void ammunitionGive() {
		Argument name = new OptionalArgument(tree, (argument, sender, args) -> {
			Player player     = (Player) sender;
			String weaponName = args[2];

			giveWeapon(player, weaponName.toLowerCase(), 1);
		});
	}

	private void giveWeapon(Player player, String name, int amount) {
		int    amountLeft = amount;
		Weapon weapon     = WeaponAddon.getWeapon(name);

		if (weapon == null) {
			player.sendMessage("Invalid weapon!");
			return;
		}

		PlayerInventory inventory = player.getInventory();

		for (int i = 0; i < inventory.getContents().length; i++) {
			ItemStack itemStack = inventory.getItem(i);

			if (itemStack != null) continue;
			// check if no amount needs to be given
			if (amountLeft == 0) break;

//			player.getInventory().setItem(i, );

			--amountLeft;
		}
	}

}
