package me.luckyraven.sign.aspect;

import lombok.RequiredArgsConstructor;
import me.luckyraven.sign.model.ParsedSign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
			if (player.getInventory().firstEmpty() == -1) {
				return AspectResult.failure("Your inventory is full!");
			}

			player.getInventory().addItem(item);

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
			return player.getInventory().firstEmpty() != -1;
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
