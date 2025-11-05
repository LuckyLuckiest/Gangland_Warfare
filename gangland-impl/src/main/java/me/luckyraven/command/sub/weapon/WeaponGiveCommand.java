package me.luckyraven.command.sub.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;

class WeaponGiveCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;

	protected WeaponGiveCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "give", tree, parent);

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
		Argument name = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player  player     = (Player) sender;
			String  weaponName = args[2];
			boolean giveWeapon = giveWeapon(player, weaponName.toLowerCase(), 1);

			if (giveWeapon) player.sendMessage(ChatUtil.commandMessage("Given &b" + weaponName + "&7."));
			else player.sendMessage(ChatUtil.errorMessage("Invalid weapon!"));
		});

		Argument amount = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player player     = (Player) sender;
			String weaponName = args[2];
			int    weaponAmount;

			try {
				weaponAmount = Integer.parseInt(args[3]);
			} catch (NumberFormatException exception) {
				player.sendMessage(ChatUtil.commandMessage(MessageAddon.MUST_BE_NUMBERS.toString()));
				return;
			}

			boolean giveWeapon = giveWeapon(player, weaponName.toLowerCase(), weaponAmount);

			if (giveWeapon) player.sendMessage(
					ChatUtil.commandMessage("Given &a" + weaponAmount + " &b" + weaponName + "&7."));
			else player.sendMessage(ChatUtil.errorMessage("Invalid weapon!"));
		});

		name.addSubArgument(amount);
		this.addSubArgument(name);
	}

	private boolean giveWeapon(Player player, String name, int amount) {
		Weapon weapon = gangland.getInitializer().getWeaponManager().getWeapon(player, null, name, true);

		if (weapon == null) return false;

		int             slots      = (int) Math.ceil(amount / 64D);
		int             amountLeft = amount;
		PlayerInventory inventory  = player.getInventory();
		ItemStack[]     items      = new ItemStack[slots];

		for (int i = 0; i < inventory.getStorageContents().length; i++) {
			int amountGive = amountLeft % 65;

			if (amountGive <= 0) break;

			items[i]   = weapon.buildItem();
			amountLeft = Math.max(0, amountLeft - 1);
		}

		Map<Integer, ItemStack> left = inventory.addItem(items);

		// make the player drop from their inventory the rest of items
		for (ItemStack item : left.values()) {
			player.getWorld().dropItemNaturally(player.getLocation(), item);
		}

		return true;
	}

}
