package org.luckyraven.gangland.lootchest.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.lootchest.LootChestManager;
import org.luckyraven.gangland.lootchest.LootChestWand;
import org.luckyraven.gangland.lootchest.config.LootChestMessagesProvider;

class LootChestWandEditCommand extends SubArgument {

	private final JavaPlugin              gangland;
	private final LootChestManager        lootChestManager;
	private final LootChestMessagesProvider messagesProvider;

	protected LootChestWandEditCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                                   LootChestManager lootChestManager, LootChestMessagesProvider messagesProvider) {
		super(gangland, "edit", tree, parent);

		this.gangland         = gangland;
		this.lootChestManager = lootChestManager;
		this.messagesProvider = messagesProvider;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return ((argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;

			ItemStack heldItem = player.getInventory().getItemInMainHand();

			if (!LootChestWand.isLootChestWand(heldItem)) {
				player.sendMessage(messagesProvider.getRequiresWand());
				return;
			}

			// Open the configuration inventory
			LootChestWand wand = LootChestWand.getWand(heldItem, gangland, lootChestManager);

			if (wand == null) return;

			wand.openConfigInventory(player, "BLACK_STAINED_GLASS_PANE", " ");
		});
	}

}
