package org.luckyraven.gangland.gadget.command;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.function.Supplier;

/**
 * The stack-split / {@code addItem} / drop-leftovers body shared by the jetpack and grapple {@code give} commands.
 */
final class GiveItems {

	private GiveItems() {
	}

	/**
	 * Gives {@code amount} copies of the item {@code factory} builds, split into stacks of the item's max stack size and
	 * capped at 36 stacks; whatever the inventory cannot take is dropped at the player.
	 *
	 * @return the actual number of items given (after the 36-stack cap)
	 */
	static int give(Player player, Supplier<ItemStack> factory, int amount) {
		ItemStack       sampleItem   = factory.get();
		int             maxStackSize = sampleItem.getMaxStackSize();
		// Defensive floor/cap (review I2): the command layer already rejects amount <= 0 before calling this
		// method, but clamping here too means a non-positive amount can never produce a negative array size
		// (NegativeArraySizeException) regardless of caller.
		int             cappedAmount = Math.max(0, Math.min(amount, 36 * maxStackSize));
		int             slots        = (int) Math.ceil(cappedAmount / (double) maxStackSize);
		int             amountLeft   = cappedAmount;
		PlayerInventory inventory    = player.getInventory();
		ItemStack[]     items        = new ItemStack[slots];

		for (int i = 0; i < items.length; ++i) {
			int amountGive = Math.min(amountLeft, maxStackSize);

			if (amountGive <= 0) break;

			ItemStack item = factory.get();
			item.setAmount(amountGive);
			items[i] = item;
			amountLeft -= amountGive;
		}

		Map<Integer, ItemStack> left = inventory.addItem(items);

		World world = player.getWorld();
		if (world != null) {
			for (ItemStack item : left.values()) {
				world.dropItemNaturally(player.getLocation(), item);
			}
		}

		return cappedAmount;
	}

}
