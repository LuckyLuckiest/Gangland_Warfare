package me.luckyraven.command.sub.lootchest;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.lootchest.LootChestWand;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

class LootChestWandEditCommand extends SubArgument {

	private final Gangland gangland;

	protected LootChestWandEditCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "edit", tree, parent);

		this.gangland = gangland;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return ((argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;

			ItemStack heldItem = player.getInventory().getItemInMainHand();

			if (!LootChestWand.isLootChestWand(heldItem)) {
				player.sendMessage(ChatUtil.commandMessage("You must be holding a Loot Chest Wand to edit settings!"));
				return;
			}

			// Open the configuration inventory
			LootChestWand wand = LootChestWand.getWand(heldItem, gangland);

			if (wand == null) return;

			wand.openConfigInventory(player);
		});
	}

}
