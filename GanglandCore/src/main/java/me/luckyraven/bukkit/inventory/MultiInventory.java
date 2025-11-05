package me.luckyraven.bukkit.inventory;

import com.google.common.base.Preconditions;
import me.luckyraven.Gangland;
import me.luckyraven.TriConsumer;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.data.user.User;
import me.luckyraven.datastructure.LinkedList;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.InventoryUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


public class MultiInventory extends InventoryHandler {

	private static long                         ID = 0;
	private final  Gangland                     gangland;
	private final  LinkedList<InventoryHandler> inventories;
	private        int                          currentPage;

	public MultiInventory(Gangland gangland, String title, int size, User<Player> user) {
		super(gangland, title, size, user,
			  new NamespacedKey(gangland, titleRefactor(String.format("%s_%d", title, ++ID))), false);
		this.gangland    = gangland;
		this.inventories = new LinkedList<>();
		this.currentPage = 0;

		this.inventories.add(this);
	}

	@Nullable
	public static MultiInventory dynamicMultiInventory(Gangland gangland, User<Player> user, List<ItemStack> items,
													   String name, boolean staticItemsAllowed, boolean fixedSize,
													   @Nullable Map<ItemStack, TriConsumer<Player, InventoryHandler, ItemBuilder>> staticItems) {
		if (staticItemsAllowed) {
			if (staticItems == null || staticItems.size() <= 6) return null;
//			Preconditions.checkNotNull(staticItems, "No static items set");
//			Preconditions.checkArgument(staticItems.size() <= 6, "Can't add more items than max rows");
		}

		PageConfig cfg = computeConfigForCreation(items.size(), staticItemsAllowed, fixedSize);

		// the first page
		MultiInventory multi = new MultiInventory(gangland, name, cfg.initialPage(), user);

		multi.addItems(multi, items, 0, items.size(), staticItemsAllowed, staticItems);
		InventoryUtil.createBoarder(multi);
		// if there is a static items column, then create a line at column 2
		if (staticItemsAllowed) InventoryUtil.verticalLine(multi, 2, InventoryUtil.getFillItem(),
														   SettingAddon.getInventoryFillName(), true);

		// the other pages
		for (int i = 1; i < cfg.pages(); i++) {
			int size = i == cfg.pages() - 1 ? cfg.finalPage() : cfg.initialPage();

			NamespacedKey    key = new NamespacedKey(gangland, titleRefactor(String.format("%s_%d", name, ++ID)));
			InventoryHandler inv = new InventoryHandler(gangland, name, size, user, key, false);

			int startIndex = i * cfg.perPage();
			int endIndex   = Math.min(startIndex + cfg.perPage(), items.size());

			multi.addItems(inv, items, startIndex, endIndex, staticItemsAllowed, staticItems);
			InventoryUtil.createBoarder(inv);
			// if there is a static items column, then create a line at column 2
			if (staticItemsAllowed) InventoryUtil.verticalLine(inv, 2, InventoryUtil.getFillItem(),
															   SettingAddon.getInventoryFillName(), true);

			multi.addPage(inv);
		}

		// add the navigation buttons
		for (LinkedList.Node<InventoryHandler> node : multi.inventories) {
			if (node.getNext() != null) {
				MultiInventoryNavigation.addNavigationButtons(node.getData(), node.getNext().getData(),
															  multi.getDisplayTitle(), multi.currentPage,
															  multi.inventories.getSize(),
															  player -> multi.nextPage().open(player),
															  player -> multi.previousPage().open(player),
															  player -> multi.homePage().open(player));
			}
		}

		return multi;
	}

	private static PageConfig computeConfigForCreation(int itemsCount, boolean staticItemsAllowed, boolean fixedSize) {
		int maxRows         = 4;
		int maxColumns      = staticItemsAllowed ? 6 : 7;
		int perPage         = maxColumns * maxRows;
		int pages           = (int) Math.ceil((double) itemsCount / perPage);
		int remainingAmount = itemsCount % perPage;
		int finalPage       = remainingAmount + 9 * 2 + (int) Math.ceil((double) remainingAmount / 9);
		int initialPage     = pages == 1 ? finalPage : InventoryHandler.MAX_SLOTS;
		if (fixedSize) {
			finalPage   = InventoryHandler.MAX_SLOTS;
			initialPage = InventoryHandler.MAX_SLOTS;
		}
		return new PageConfig(maxRows, maxColumns, perPage, pages, remainingAmount, finalPage, initialPage);
	}

	private static PageConfig computeConfigForUpdate(int itemsCount, int inventorySize) {
		int maxRows         = 4;
		int maxColumns      = inventorySize / 9;
		int perPage         = maxColumns * maxRows;
		int pages           = (int) Math.ceil((double) itemsCount / perPage);
		int remainingAmount = itemsCount % perPage;
		int finalPage       = remainingAmount + 9 * 2 + (int) Math.ceil((double) remainingAmount / 9);
		int initialPage     = pages == 1 ? finalPage : InventoryHandler.MAX_SLOTS;
		return new PageConfig(maxRows, maxColumns, perPage, pages, remainingAmount, finalPage, initialPage);
	}

	public void updateItems(List<ItemStack> items, User<Player> user, boolean staticItemsAllowed,
							@Nullable Map<ItemStack, TriConsumer<Player, InventoryHandler, ItemBuilder>> staticItems) {
		if (inventories.isEmpty()) {
			return; // No inventories to update
		}

		PageConfig cfg = computeConfigForUpdate(items.size(), inventories.getHead().getData().getSize());

		int              inventoryIndex = 0;
		InventoryHandler firstPage      = inventories.getHead().getData();

		// Update the first page with new items
		firstPage.clear();

		addItems(firstPage, items, 0, Math.min(cfg.perPage(), items.size()), staticItemsAllowed, staticItems);
		InventoryUtil.createBoarder(firstPage);
		if (staticItemsAllowed) InventoryUtil.verticalLine(firstPage, 2, InventoryUtil.getFillItem(),
														   SettingAddon.getInventoryFillName(), true);

		for (int i = 1; i < cfg.pages(); i++) {
			int              size = i == cfg.pages() - 1 ? cfg.finalPage() : cfg.initialPage();
			InventoryHandler inv;

			if (i >= inventories.getSize()) {
				// If there's no corresponding inventory for this page, create a new one
				NamespacedKey namespacedKey = new NamespacedKey(gangland, titleRefactor(
						String.format("%s_%d", firstPage.getDisplayTitle(), ++ID)));
				inv = new InventoryHandler(gangland, firstPage.getDisplayTitle(), size, user, namespacedKey, false);
				addItems(inv, items, i * cfg.perPage(), Math.min((i + 1) * cfg.perPage(), items.size()),
						 staticItemsAllowed,
						 staticItems);
				addPage(inv);
			} else {
				// If there's already an inventory for this page, update its items and title
				inv = inventories.get(i);

				if (inv == null) continue;

				inv.clear();
				addItems(inv, items, i * cfg.perPage(), Math.min((i + 1) * cfg.perPage(), items.size()), false, null);
			}
			InventoryUtil.createBoarder(inv);
			if (staticItemsAllowed) InventoryUtil.verticalLine(inv, 2, InventoryUtil.getFillItem(),
															   SettingAddon.getInventoryFillName(), true);

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

	public List<InventoryHandler> getLinkedInventories() {
		return Collections.unmodifiableList(inventories.toList());
	}

	private void addItems(InventoryHandler inv, List<ItemStack> items, int startIndex, int endIndex,
						  boolean staticItemsAllowed,
						  @Nullable Map<ItemStack, TriConsumer<Player, InventoryHandler, ItemBuilder>> staticItems) {
		if (staticItemsAllowed) {
			Preconditions.checkNotNull(staticItems, "No static items set");
			Preconditions.checkArgument(staticItems.size() <= 6, "Can't add more items than max rows");
		}

		int additional = staticItemsAllowed ? 1 : 0;
		int row        = 2;
		int column     = 2 + additional;

		if (staticItemsAllowed) verticalLine(inv, 1, staticItems);

		for (int i = startIndex; i < endIndex && row % 6 != 0; i++) {
			inv.setItem((row - 1) * 9 + (column - 1), items.get(i), false);

			if (column % 8 == 0) {
				column = 2;
				++row;
			} else ++column;
		}
	}

	private void verticalLine(InventoryHandler inventoryHandler, int column,
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
			else inventoryHandler.getInventory()
								 .setItem(slot, new ItemBuilder(InventoryUtil.getLineItem()).setDisplayName(
										 SettingAddon.getInventoryLineName()).build());

		}
	}

}
