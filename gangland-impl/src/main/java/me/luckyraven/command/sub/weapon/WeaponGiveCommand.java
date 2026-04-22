package me.luckyraven.command.sub.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.weapon.WeaponLoader;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Map;

class WeaponGiveCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final WeaponManager       weaponManager;
	private final WeaponLoader        weaponLoader;

	protected WeaponGiveCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                            UserManager<Player> userManager,
	                            WeaponManager weaponManager,
	                            WeaponLoader weaponLoader) {
		super(gangland, "give", tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.userManager   = userManager;
		this.weaponManager = weaponManager;
		this.weaponLoader  = weaponLoader;

		weaponGive();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
	}

	private void weaponGive() {
		Argument name = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			String  weaponName = args[2];
			boolean giveWeapon = giveWeapon(player, weaponName.toLowerCase(), 1);

			if (giveWeapon) {
				String receivedWeapon = Messages.RECEIVED_WEAPON.toString();
				user.sendMessage(receivedWeapon.replace("%weapon%", weaponName).replace("%amount%", "1"));
			} else {
				String invalidWeapon = Messages.INVALID_WEAPON.toString();
				user.sendMessage(invalidWeapon.replace("%args%", weaponName));
			}
		}, sender -> {
			return weaponLoader.getFiles()
					.stream().map(FileHandler::getName).toList();
		});

		Argument amount = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			String weaponName = args[2];
			int    weaponAmount;

			try {
				weaponAmount = Integer.parseInt(args[3]);
			} catch (NumberFormatException exception) {
				user.sendMessage(GanglandChatUtil.commandMessage(Messages.MUST_BE_NUMBERS.toString()));
				return;
			}

			boolean giveWeapon = giveWeapon(player, weaponName.toLowerCase(), weaponAmount);

			if (giveWeapon) {
				String receivedWeapon = Messages.RECEIVED_WEAPON.toString();
				String replace = receivedWeapon.replace("%weapon%", weaponName)
				                               .replace("%amount%", String.valueOf(weaponAmount));
				user.sendMessage(replace);
			} else {
				String invalidWeapon = Messages.INVALID_WEAPON.toString();
				user.sendMessage(invalidWeapon.replace("%args%", weaponName));
			}
		}, sender -> List.of("<amount>"));

		name.addSubArgument(amount);
		this.addSubArgument(name);
	}

	private boolean giveWeapon(Player player, String name, int amount) {
		Weapon weapon = weaponManager.getWeapon(player, null, name, true);

		if (weapon == null) return false;

		ItemStack       sampleItem   = weapon.buildItem(player);
		int             maxStackSize = sampleItem.getMaxStackSize();
		int             slots        = (int) Math.ceil(amount / (double) maxStackSize);
		int             amountLeft   = amount;
		PlayerInventory inventory    = player.getInventory();
		ItemStack[]     items        = new ItemStack[slots];

		for (int i = 0; i < slots; i++) {
			int amountGive = Math.min(amountLeft, maxStackSize);

			if (amountGive <= 0) break;

			ItemStack item = weapon.buildItem(player);

			item.setAmount(amountGive);

			items[i] = item;

			amountLeft -= amountGive;
		}

		Map<Integer, ItemStack> left = inventory.addItem(items);

		// make the player drop from their inventory the rest of items
		for (ItemStack item : left.values()) {
			player.getWorld().dropItemNaturally(player.getLocation(), item);
		}

		return true;
	}

}
