package me.luckyraven.command.sub.item.repair;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gadget.repair.RepairManager;
import me.luckyraven.gadget.repair.material.RepairMaterial;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Map;

class ItemRepairGiveCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	ItemRepairGiveCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "give", tree, parent);

		this.gangland    = gangland;
		this.tree        = tree;
		this.userManager = gangland.getInitializer().getUserManager();

		repairGive();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
	}

	private void repairGive() {
		Argument name = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			String  itemName = args[3];
			boolean gave     = giveRepairItem(player, itemName, 1);

			if (gave) {
				user.sendMessage(GanglandChatUtil.commandMessage("Gave &b" + itemName + " &7x&b1&7."));
			} else {
				user.sendMessage(GanglandChatUtil.prefixMessage("Invalid repair item: &c" + itemName));
			}
		}, sender -> {
			RepairManager repairManager = gangland.getInitializer().getRepairManager();
			return repairManager.getMaterialManager().getAllMaterials().keySet()
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

			boolean gave = giveRepairItem(player, itemName, itemAmount);

			if (gave) {
				user.sendMessage(GanglandChatUtil.commandMessage(
						"Gave &b" + itemName + " &7x&b" + itemAmount + "&7."));
			} else {
				user.sendMessage(GanglandChatUtil.prefixMessage("Invalid repair item: &c" + itemName));
			}
		}, sender -> List.of("<amount>"));

		name.addSubArgument(amount);
		this.addSubArgument(name);
	}

	private boolean giveRepairItem(Player player, String name, int amount) {
		RepairManager  repairManager  = gangland.getInitializer().getRepairManager();
		RepairMaterial repairMaterial = repairManager.getMaterialManager().getMaterial(name);

		if (repairMaterial == null) return false;

		ItemStack       sampleItem   = repairMaterial.buildItem();
		int             maxStackSize = sampleItem.getMaxStackSize();
		int             slots        = (int) Math.ceil(amount / (double) maxStackSize);
		int             amountLeft   = amount;
		PlayerInventory inventory    = player.getInventory();
		ItemStack[]     items        = new ItemStack[slots];

		for (int i = 0; i < items.length; ++i) {
			int amountGive = Math.min(amountLeft, maxStackSize);

			if (amountGive <= 0) break;

			ItemStack item = repairMaterial.buildItem();

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
