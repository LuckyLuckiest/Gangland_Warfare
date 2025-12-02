package me.luckyraven.inventory.multi;

import com.google.common.base.Preconditions;
import lombok.AccessLevel;
import lombok.Getter;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.part.PageConfig;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.LinkedList;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static me.luckyraven.inventory.multi.MultiInventoryCreation.computeConfigForUpdate;
import static me.luckyraven.inventory.util.InventoryUtil.titleRefactor;


public class MultiInventory extends InventoryHandler {

	static long ID = 0;

	private final LinkedList<InventoryHandler> inventories;

	@Getter(AccessLevel.PACKAGE)
	private int currentPage;

	public MultiInventory(JavaPlugin plugin, String title, int size, Player player) {
		super(title, size, player, new NamespacedKey(plugin, titleRefactor(String.format("%s_%d", title, ++ID))));

		this.inventories = new LinkedList<>();
		this.currentPage = 0;

		this.inventories.add(this);
	}

	public void updateItems(JavaPlugin plugin, List<ItemStack> items, Player player, boolean staticItemsAllowed,
							Fill fill,
							@Nullable Map<ItemStack, TriConsumer<Player, InventoryHandler, ItemBuilder>> staticItems) {
		if (inventories.isEmpty()) {
			return; // No inventories to update
		}

		PageConfig cfg = computeConfigForUpdate(items.size(), inventories.getHead().getData().getSize());

		int              inventoryIndex = 0;
		InventoryHandler firstPage      = inventories.getHead().getData();

		// Update the first page with new items
		firstPage.clear();

		addItems(firstPage, items, 0, Math.min(cfg.perPage(), items.size()), staticItemsAllowed, fill, staticItems);
		InventoryUtil.createBoarder(firstPage, fill);
		if (staticItemsAllowed) InventoryUtil.verticalLine(firstPage, fill, 2, true);

		for (int i = 1; i < cfg.pages(); i++) {
			int              size = i == cfg.pages() - 1 ? cfg.finalPage() : cfg.initialPage();
			InventoryHandler inv;

			int startIndex = i * cfg.perPage();
			if (i >= inventories.getSize()) {
				// If there's no corresponding inventory for this page, create a new one
				NamespacedKey namespacedKey = new NamespacedKey(plugin, titleRefactor(
						String.format("%s_%d", firstPage.getDisplayTitle(), ++ID)));
				inv = new InventoryHandler(firstPage.getDisplayTitle(), size, player, namespacedKey);

				addItems(inv, items, startIndex, Math.min((i + 1) * cfg.perPage(), items.size()), staticItemsAllowed,
						 fill, staticItems);
				addPage(inv);
			} else {
				// If there's already an inventory for this page, update its items and title
				inv = inventories.get(i);

				if (inv == null) continue;

				inv.clear();
				addItems(inv, items, startIndex, Math.min((i + 1) * cfg.perPage(), items.size()), false, fill, null);
			}
			InventoryUtil.createBoarder(inv, fill);
			if (staticItemsAllowed) InventoryUtil.verticalLine(inv, fill, 2, true);

			inventoryIndex++;
		}

		// Remove any extra inventories if there were more pages before the update
		while (inventories.getSize() > inventoryIndex + 1) {
			removePage(inventories.getTail().getData());
		}
	}

	public void addPage(InventoryHandler currentInv) {
		inventories.add(currentInv);
	}

	public boolean removePage(InventoryHandler gui) {
		int current = inventories.lastIndexOf(gui);
		if (current == -1) return false;

		int next = current + 1, prev = current - 1;

		if (current == 0) if (inventories.getSize() > 1) {
			InventoryHandler nextInventory = inventories.get(next);

			if (nextInventory == null) return false;

			nextInventory.removeItem(nextInventory.getSize() - 9);
		} else if (current == inventories.getSize() - 1) {
			InventoryHandler prevInventory = inventories.get(prev);

			if (prevInventory == null) return false;

			prevInventory.removeItem(prevInventory.getSize() - 1);
		}

		inventories.remove(current);
		return true;
	}

	public InventoryHandler nextPage() {
		++currentPage;
		if (currentPage >= inventories.getSize()) currentPage = inventories.getSize() - 1;
		return inventories.get(currentPage);
	}

	public InventoryHandler previousPage() {
		--currentPage;
		if (currentPage < 0) currentPage = 0;
		return inventories.get(currentPage);
	}

	public boolean hasNextPage() {
		return currentPage < inventories.getSize();
	}

	public InventoryHandler homePage() {
		currentPage = 0;
		return inventories.getHead().getData();
	}

	public LinkedList<InventoryHandler> getLinkedInventories() {
		return inventories;
	}

	void addItems(InventoryHandler inv, List<ItemStack> items, int startIndex, int endIndex, boolean staticItemsAllowed,
				  Fill line, @Nullable Map<ItemStack, TriConsumer<Player, InventoryHandler, ItemBuilder>> staticItems) {
		if (staticItemsAllowed) {
			Preconditions.checkNotNull(staticItems, "No static items set");
			Preconditions.checkArgument(staticItems.size() <= 6, "Can't add more items than max rows");
		}

		int additional = staticItemsAllowed ? 1 : 0;
		int row        = 2;
		int column     = 2 + additional;

		if (staticItemsAllowed) verticalLine(inv, 1, line, staticItems);

		for (int i = startIndex; i < endIndex && row % 6 != 0; i++) {
			inv.setItem((row - 1) * 9 + (column - 1), items.get(i), false);

			if (column % 8 == 0) {
				column = 2;
				++row;
			} else ++column;
		}
	}

	private void verticalLine(InventoryHandler inventoryHandler, int column, Fill line,
							  Map<ItemStack, TriConsumer<Player, InventoryHandler, ItemBuilder>> staticItems) {
		Preconditions.checkArgument(column > 0 && column < 9, "Columns need to be between 1 and 9 inclusive");

		// from 1-6
		int rows = inventoryHandler.getSize() / 9;

		List<ItemStack>                                          items     = new ArrayList<>();
		List<TriConsumer<Player, InventoryHandler, ItemBuilder>> consumers = new ArrayList<>();

		for (Map.Entry<ItemStack, TriConsumer<Player, InventoryHandler, ItemBuilder>> entry : staticItems.entrySet()) {
			items.add(entry.getKey());
			consumers.add(entry.getValue());
		}

		for (int i = 0; i < rows; ++i) {
			int slot = (column - 1) + 9 * i;

			if (inventoryHandler.getInventory().getItem(slot) != null) continue;

			if (i < staticItems.size()) inventoryHandler.setItem(slot, items.get(i), false, consumers.get(i));
			else {
				ItemBuilder item = new ItemBuilder(InventoryUtil.getLineItem(line.material())).setDisplayName(
						line.name());

				inventoryHandler.getInventory().setItem(slot, item.build());
			}
		}
	}

}
