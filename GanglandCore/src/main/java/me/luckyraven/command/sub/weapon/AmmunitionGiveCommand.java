package me.luckyraven.command.sub.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.feature.weapon.ammo.Ammunition;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

class AmmunitionGiveCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;

	protected AmmunitionGiveCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super("give", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		ammunitionGive();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
	}

	private void ammunitionGive() {
		Argument name = new OptionalArgument(tree, (argument, sender, args) -> {
			Player player   = (Player) sender;
			String ammoName = args[2];

			giveAmmunition(player, ammoName.toLowerCase(), 1);
			player.sendMessage(ChatUtil.commandMessage("Given &a1 &b" + ammoName + "&7."));
		});

		Argument amount = new OptionalArgument(tree, (argument, sender, args) -> {
			Player player   = (Player) sender;
			String ammoName = args[2];
			int    ammoAmount;

			try {
				ammoAmount = Integer.parseInt(args[3]);
			} catch (NumberFormatException exception) {
				player.sendMessage(ChatUtil.commandMessage(MessageAddon.MUST_BE_NUMBERS.toString()));
				return;
			}

			giveAmmunition(player, ammoName.toLowerCase(), ammoAmount);
			player.sendMessage(ChatUtil.commandMessage("Given &a" + ammoAmount + " &b" + ammoName + "&7."));
		});

		name.addSubArgument(amount);
		this.addSubArgument(name);
	}

	private void giveAmmunition(Player player, String name, int amount) {
		int        slots      = (int) Math.ceil(amount / 64D);
		int        amountLeft = amount;
		Ammunition ammunition = gangland.getInitializer().getAmmunitionAddon().getAmmunition(name);

		if (ammunition == null) {
			player.sendMessage("Invalid ammunition!");
			return;
		}

		PlayerInventory inventory = player.getInventory();

		for (int i = 0; i < inventory.getStorageContents().length; i++) {
			ItemStack itemStack = inventory.getItem(i);

			if (itemStack != null) continue;
			// check if no amount needs to be given
			if (slots == 0) break;

			int amountGive = amountLeft % 65;

			player.getInventory().setItem(i, ammunition.getItem(amountGive));

			amountLeft -= amountGive;
			--slots;
		}

		// make the player drop from their inventory the rest of items
		while (amountLeft > 0) {
			player.getWorld().dropItemNaturally(player.getLocation(), ammunition.getItem());
			--amountLeft;
		}
	}

}
