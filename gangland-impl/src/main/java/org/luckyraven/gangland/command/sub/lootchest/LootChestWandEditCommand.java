package org.luckyraven.gangland.command.sub.lootchest;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.inventory.part.Fill;
import org.luckyraven.gangland.lootchest.LootChestManager;
import org.luckyraven.gangland.lootchest.LootChestWand;

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
