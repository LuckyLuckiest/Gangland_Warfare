package me.luckyraven.command.sub.item.money;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.item.money.MoneyAddon;
import me.luckyraven.item.money.MoneyDepositService;
import me.luckyraven.item.money.MoneyItem;
import me.luckyraven.item.money.MoneyItemFactory;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Map;

class ItemMoneyGiveCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MoneyAddon          moneyAddon;
	private final MoneyDepositService moneyDepositService;

	ItemMoneyGiveCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                     UserManager<Player> userManager,
	                     MoneyAddon moneyAddon,
	                     MoneyDepositService moneyDepositService) {
		super(gangland, "give", tree, parent);

		this.gangland            = gangland;
		this.tree                = tree;
		this.userManager         = userManager;
		this.moneyAddon          = moneyAddon;
		this.moneyDepositService = moneyDepositService;

		moneyGive();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
	}

	private void moneyGive() {
		Argument name = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			String  itemName = args[3];
			boolean gave     = giveMoneyItem(player, itemName, 1);

			if (gave) {
				user.sendMessage(Messages.ITEM_MONEY_GAVE.toString()
				                                         .replace("%name%", itemName)
				                                         .replace("%amount%", "1"));
			} else {
				user.sendMessage(Messages.ITEM_MONEY_INVALID.toString().replace("%name%", itemName));
			}
		}, sender -> {
			return moneyAddon.getVariations().keySet()
					.stream().toList();
		});

		Argument amount = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			String itemName = args[3];
			int    itemAmount;

			try {
				itemAmount = Integer.parseInt(args[4]);
			} catch (NumberFormatException exception) {
				user.sendMessage(GanglandChatUtil.commandMessage(Messages.MUST_BE_NUMBERS.toString()));
				return;
			}

			boolean gave = giveMoneyItem(player, itemName, itemAmount);

			if (gave) {
				user.sendMessage(Messages.ITEM_MONEY_GAVE.toString()
				                                         .replace("%name%", itemName)
				                                         .replace("%amount%", String.valueOf(itemAmount)));
			} else {
				user.sendMessage(Messages.ITEM_MONEY_INVALID.toString().replace("%name%", itemName));
			}
		}, sender -> List.of("<amount>"));

		name.addSubArgument(amount);
		this.addSubArgument(name);
	}

	private boolean giveMoneyItem(Player player, String name, int amount) {
		MoneyItem variation = moneyAddon.getVariation(name);

		if (variation == null) return false;
		if (amount <= 0) return true;

		// Roll once and build a single stack so identical-NBT items merge for stackable materials. Items larger than
		// one slot are split across slots; non-stackable materials (max stack size 1) naturally produce N separate
		// stacks-of-one.
		int rolled = moneyAddon.rollAmount(variation);

		ItemStack template     = MoneyItemFactory.build(variation, rolled, moneyDepositService);
		int       maxStackSize = template.getMaxStackSize();
		int       slots        = (int) Math.ceil(amount / (double) maxStackSize);
		int       amountLeft   = amount;

		PlayerInventory inventory = player.getInventory();
		ItemStack[]     items     = new ItemStack[slots];

		for (int i = 0; i < slots; i++) {
			int amountGive = Math.min(amountLeft, maxStackSize);
			if (amountGive <= 0) break;

			items[i] = MoneyItemFactory.build(variation, rolled, moneyDepositService, amountGive);
			amountLeft -= amountGive;
		}

		Map<Integer, ItemStack> left = inventory.addItem(items);

		for (ItemStack item : left.values()) {
			player.getWorld().dropItemNaturally(player.getLocation(), item);
		}

		return true;
	}

}
