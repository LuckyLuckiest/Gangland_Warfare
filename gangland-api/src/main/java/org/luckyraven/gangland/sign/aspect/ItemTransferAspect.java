package org.luckyraven.gangland.sign.aspect;

import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.sign.model.ParsedSign;

import java.util.Map;

@RequiredArgsConstructor
public class ItemTransferAspect implements SignAspect {

	private final ItemProvider          itemProvider;
	private final TransferType          transferType;
	private final ItemSimilarityChecker similarityChecker;

	@Override
	public AspectResult execute(Player player, ParsedSign sign) {
		ItemStack item    = itemProvider.getItem(sign);
		String    content = sign.getContent();
		int       amount  = sign.getAmount();

		if (item == null) {
			return AspectResult.failure("Invalid item: " + content);
		}

		item.setAmount(amount);

		if (transferType == TransferType.GIVE) {
			// One free slot is not enough: the sign can hand over more than a single stack, and the leftover map
			// addItem returns used to be discarded — voiding the excess after the payment had already been taken.
			if (!hasSpaceFor(player, item, amount)) {
				return AspectResult.failure("Your inventory is full!");
			}

			Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);

			// Belt and braces: if Bukkit still could not fit everything, drop the remainder at the player's feet
			// rather than voiding items the player has been charged for.
			dropLeftover(player, leftover);

			return AspectResult.successContinue("Received " + amount + "x " + content);
		} else {
			boolean containsItem = hasEnoughItems(player, item, amount);

			if (!containsItem) {
				return AspectResult.failure("You don't have enough " + content + "!");
			}

			removeItems(player, item, amount);

			return AspectResult.successContinue("Sold " + amount + "x " + content);
		}
	}

	@Override
	public boolean canExecute(Player player, ParsedSign sign) {
		ItemStack item = itemProvider.getItem(sign);

		if (item == null) {
			return false;
		}

		if (transferType == TransferType.GIVE) {
			return hasSpaceFor(player, item, sign.getAmount());
		} else {
			ItemStack cleanItem = item.clone();
			cleanItem.setAmount(sign.getAmount());

			return hasEnoughItems(player, cleanItem, sign.getAmount());
		}
	}

	@Override
	public String getName() {
		return "ItemTransferAspect-" + transferType;
	}

	@Override
	public int getPriority() {
		return 50;
	}

	/**
	 * Whether the player's storage can absorb {@code amount} more of {@code item}: every empty slot counts for a full
	 * stack, and a partially filled similar stack counts for its remaining room.
	 */
	private boolean hasSpaceFor(Player player, ItemStack item, int amount) {
		if (amount <= 0) {
			return true;
		}

		int maxStackSize = Math.max(1, item.getMaxStackSize());
		int free         = 0;

		for (ItemStack slot : player.getInventory().getStorageContents()) {
			if (slot == null || slot.getType() == Material.AIR) {
				free += maxStackSize;
			} else if (isSimilarItems(player, slot, item)) {
				free += Math.max(0, slot.getMaxStackSize() - slot.getAmount());
			}

			if (free >= amount) {
				return true;
			}
		}

		return free >= amount;
	}

	private void dropLeftover(Player player, Map<Integer, ItemStack> leftover) {
		if (leftover == null || leftover.isEmpty()) {
			return;
		}

		World world = player.getWorld();
		if (world == null) {
			return;
		}

		for (ItemStack drop : leftover.values()) {
			world.dropItemNaturally(player.getLocation(), drop);
		}
	}

	private void removeItems(Player player, ItemStack requiredItem, int amountToRemove) {
		int remaining = amountToRemove;

		ItemStack[] contents = player.getInventory().getStorageContents();
		for (int i = 0; i < contents.length && remaining > 0; i++) {
			ItemStack inventoryItem = contents[i];

			if (inventoryItem == null) {
				continue;
			}

			if (isSimilarItems(player, inventoryItem, requiredItem)) {
				int stackAmount = inventoryItem.getAmount();

				if (stackAmount <= remaining) {
					// Remove entire stack
					contents[i] = null;
					remaining -= stackAmount;
				} else {
					// Remove partial stack
					inventoryItem.setAmount(stackAmount - remaining);
					remaining = 0;
				}
			}
		}

		player.getInventory().setStorageContents(contents);
	}

	private boolean hasEnoughItems(Player player, ItemStack requiredItem, int requiredAmount) {
		int totalCount = 0;

		for (ItemStack item : player.getInventory().getStorageContents()) {
			if (item == null) {
				continue;
			}

			if (isSimilarItems(player, item, requiredItem)) {
				totalCount += item.getAmount();

				if (totalCount >= requiredAmount) {
					return true;
				}
			}
		}

		return false;
	}

	private boolean isSimilarItems(Player player, ItemStack item1, ItemStack item2) {
		return similarityChecker.isSimilar(player, item1, item2);
	}

	public enum TransferType {
		GIVE,
		TAKE;
	}

	@FunctionalInterface
	public interface ItemProvider {

		ItemStack getItem(ParsedSign sign);

	}

	@FunctionalInterface
	public interface ItemSimilarityChecker {

		boolean isSimilar(Player player, ItemStack a, ItemStack b);

	}

}
