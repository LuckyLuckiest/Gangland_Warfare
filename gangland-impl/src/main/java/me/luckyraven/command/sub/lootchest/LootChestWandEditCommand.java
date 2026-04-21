package me.luckyraven.command.sub.lootchest;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.lootchest.LootChestManager;
import me.luckyraven.lootchest.LootChestWand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

class LootChestWandEditCommand extends SubArgument {

	private final Gangland         gangland;
	private final LootChestManager lootChestManager;

	protected LootChestWandEditCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                                   LootChestManager lootChestManager) {
		super(gangland, "edit", tree, parent);

		this.gangland         = gangland;
		this.lootChestManager = lootChestManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return ((argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;

			ItemStack heldItem = player.getInventory().getItemInMainHand();

			if (!LootChestWand.isLootChestWand(heldItem)) {
				player.sendMessage(Messages.LOOT_CHEST_REQUIRES_WAND.toString());
				return;
			}

			// Open the configuration inventory
			LootChestWand wand = LootChestWand.getWand(heldItem, gangland, lootChestManager);

			if (wand == null) return;

			Fill fill = new Fill(Settings.getInventoryFillName(), Settings.getInventoryFillItem());
			wand.openConfigInventory(player, fill);
		});
	}

}
