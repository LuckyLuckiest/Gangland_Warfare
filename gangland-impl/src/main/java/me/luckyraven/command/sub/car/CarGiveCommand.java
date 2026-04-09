package me.luckyraven.command.sub.car;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gadget.car.Car;
import me.luckyraven.gadget.car.config.CarAddon;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Map;

class CarGiveCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final CarAddon            carAddon;

	CarGiveCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	               UserManager<Player> userManager, CarAddon carAddon) {
		super(gangland, "give", tree, parent);

		this.gangland    = gangland;
		this.tree        = tree;
		this.userManager = userManager;
		this.carAddon    = carAddon;

		carGive();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
	}

	private void carGive() {
		Argument name = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			String  carName = args[2];
			boolean gave    = giveCarItem(player, carName, 1);

			if (gave) {
				user.sendMessage(GanglandChatUtil.commandMessage("Gave &b" + carName + " &7x&b1&7."));
			} else {
				user.sendMessage(GanglandChatUtil.prefixMessage("Invalid car: &c" + carName));
			}
		}, sender -> carAddon.getCars().keySet()
				.stream().toList());

		Argument amount = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			String carName = args[2];
			int    carAmount;

			try {
				carAmount = Integer.parseInt(args[3]);
			} catch (NumberFormatException exception) {
				user.sendMessage(GanglandChatUtil.commandMessage(Messages.MUST_BE_NUMBERS.toString()));
				return;
			}

			boolean gave = giveCarItem(player, carName, carAmount);

			if (gave) {
				user.sendMessage(GanglandChatUtil.commandMessage(
						"Gave &b" + carName + " &7x&b" + carAmount + "&7."));
			} else {
				user.sendMessage(GanglandChatUtil.prefixMessage("Invalid car: &c" + carName));
			}
		}, sender -> List.of("<amount>"));

		name.addSubArgument(amount);
		this.addSubArgument(name);
	}

	private boolean giveCarItem(Player player, String name, int amount) {
		Car car = carAddon.getCar(name);

		if (car == null) return false;

		ItemStack       sampleItem   = car.buildItem(player);
		int             maxStackSize = sampleItem.getMaxStackSize();
		int             slots        = (int) Math.ceil(amount / (double) maxStackSize);
		int             amountLeft   = amount;
		PlayerInventory inventory    = player.getInventory();
		ItemStack[]     items        = new ItemStack[slots];

		for (int i = 0; i < items.length; ++i) {
			int amountGive = Math.min(amountLeft, maxStackSize);

			if (amountGive <= 0) break;

			ItemStack item = car.buildItem(player);
			item.setAmount(amountGive);
			items[i] = item;
			           amountLeft -= amountGive;
		}

		Map<Integer, ItemStack> left = inventory.addItem(items);

		for (ItemStack item : left.values()) {
			player.getWorld().dropItemNaturally(player.getLocation(), item);
		}

		return true;
	}

}
