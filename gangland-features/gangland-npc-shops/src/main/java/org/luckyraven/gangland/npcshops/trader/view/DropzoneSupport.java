package org.luckyraven.gangland.npcshops.trader.view;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Dropzone geometry and item movement shared by {@link SellView} and {@link BarterView}: both panels use the same 20
 * dropzone slots and the same shift-click placement and item-return rules.
 */
final class DropzoneSupport {

	private static final int[] ALL_DROPZONE_SLOTS = {
			10, 11, 12, 13, 14,
			19, 20, 21, 22, 23,
			28, 29, 30, 31, 32,
			37, 38, 39, 40, 41
	};

	private DropzoneSupport() {
	}

	static boolean contains(int[] arr, int value) {
		for (int v : arr) if (v == value) return true;
		return false;
	}

	static int[] dropzoneSlots(int cap) {
		int   limit = Math.max(1, Math.min(cap, ALL_DROPZONE_SLOTS.length));
		int[] out   = new int[limit];
		System.arraycopy(ALL_DROPZONE_SLOTS, 0, out, 0, limit);
		return out;
	}

	/** Hands every dropzone item back to the viewer (overflow drops at their feet); a {@code null} inventory means no
	 *  menu is open, so there is nothing to return. */
	static void returnItemsToPlayer(Player viewer, Inventory inv, int[] dropzoneSlots) {
		if (inv == null) return;
		for (int slot : dropzoneSlots) {
			ItemStack stack = inv.getItem(slot);
			if (stack == null || stack.getType() == Material.AIR) continue;
			Map<Integer, ItemStack> leftover = viewer.getInventory().addItem(stack.clone());
			for (ItemStack drop : leftover.values()) {
				viewer.getWorld().dropItemNaturally(viewer.getLocation(), drop);
			}
			inv.setItem(slot, null);
		}
	}

	static int tryPlaceInDropzone(int[] dropzoneSlots, Inventory inventory, ItemStack stack) {
		int remaining  = stack.getAmount();
		int maxPerSlot = stack.getMaxStackSize();

		for (int slot : dropzoneSlots) {
			if (remaining <= 0) break;
			ItemStack current = inventory.getItem(slot);
			if (current == null || current.getType() == Material.AIR) {
				ItemStack placed = stack.clone();
				int       amount = Math.min(remaining, maxPerSlot);
				placed.setAmount(amount);
				inventory.setItem(slot, placed);
				remaining -= amount;
			} else if (current.isSimilar(stack) && current.getAmount() < maxPerSlot) {
				int space = maxPerSlot - current.getAmount();
				int add   = Math.min(space, remaining);
				current.setAmount(current.getAmount() + add);
				remaining -= add;
			}
		}
		return stack.getAmount() - remaining;
	}
}
