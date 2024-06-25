package me.luckyraven.command.sub.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

class WeaponGiveCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;

	protected WeaponGiveCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super("give", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		weaponGive();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
	}

	private void weaponGive() {
		Argument name = new OptionalArgument(tree, (argument, sender, args) -> {
			Player player     = (Player) sender;
			String weaponName = args[2];

			if (giveWeapon(player, weaponName.toLowerCase(), 1)) player.sendMessage(
					ChatUtil.commandMessage("Given &b" + weaponName + "&7."));
			else player.sendMessage(ChatUtil.errorMessage("Invalid weapon!"));
		});

		Argument amount = new OptionalArgument(tree, (argument, sender, args) -> {
			Player player     = (Player) sender;
			String weaponName = args[2];
			int    weaponAmount;

			try {
				weaponAmount = Integer.parseInt(args[3]);
			} catch (NumberFormatException exception) {
				player.sendMessage(ChatUtil.commandMessage(MessageAddon.MUST_BE_NUMBERS.toString()));
				return;
			}

			if (giveWeapon(player, weaponName.toLowerCase(), weaponAmount)) player.sendMessage(
					ChatUtil.commandMessage("Given &a" + weaponAmount + " &b" + weaponName + "&7."));
			else player.sendMessage(ChatUtil.errorMessage("Invalid weapon!"));
		});

		name.addSubArgument(amount);
		this.addSubArgument(name);
	}

	private boolean giveWeapon(Player player, String name, int amount) {
		int    amountLeft = amount;
		Weapon weapon     = gangland.getInitializer().getWeaponManager().getWeapon(player, null, name, true);

		if (weapon == null) return false;

		PlayerInventory inventory = player.getInventory();

		for (int i = 0; i < inventory.getStorageContents().length; i++) {
			ItemStack itemStack = inventory.getItem(i);

			if (itemStack != null) continue;
			// check if no amount needs to be given
			if (amountLeft == 0) break;

			player.getInventory().setItem(i, weapon.buildItem());

			--amountLeft;
		}

		// make the player drop from their inventory the rest of items
		while (amountLeft > 0) {
			player.getWorld().dropItemNaturally(player.getLocation(), weapon.buildItem());
			--amountLeft;
		}

		return true;
	}

}
