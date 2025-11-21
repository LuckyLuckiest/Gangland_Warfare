package me.luckyraven.inventory.multi;

import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.ButtonTags;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.part.PageConfig;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.LinkedList;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static me.luckyraven.inventory.multi.MultiInventoryNavigation.addNavigationButtons;
import static me.luckyraven.inventory.util.InventoryUtil.titleRefactor;

public class MultiInventoryCreation {

	@Nullable
	public static MultiInventory dynamicMultiInventory(JavaPlugin plugin, Player player, List<ItemStack> items,
													   String title, boolean staticItemsAllowed, boolean fixedSize,
													   Fill fill, ButtonTags buttonTags,
													   @Nullable Map<ItemStack, TriConsumer<Player, InventoryHandler, ItemBuilder>> staticItems) {
		if (staticItemsAllowed) {
			if (staticItems == null || staticItems.size() <= 6) return null;
		}

		PageConfig cfg = computeConfigForCreation(items.size(), staticItemsAllowed, fixedSize);

		// the first page
		MultiInventory multi = new MultiInventory(plugin, title, cfg.initialPage(), player);

		multi.addItems(multi, items, 0, items.size(), staticItemsAllowed, fill, staticItems);
		InventoryUtil.createBoarder(multi, fill);
		// if there is a static items column, then create a line at column 2
		if (staticItemsAllowed) InventoryUtil.verticalLine(multi, fill, 2, true);

		// the other pages
		int totalPages = cfg.pages();

		for (int page = 1; page < totalPages; page++) {
			int  size = page == totalPages - 1 ? cfg.finalPage() : cfg.initialPage();
			long id   = MultiInventory.ID;

			String format          = String.format("%s_%d", title, id);
			String titleRefactored = titleRefactor(format);

			MultiInventory.ID = id + 1;

			InventoryHandler inv = new InventoryHandler(plugin, titleRefactored, size, player);

			int startIndex = page * cfg.perPage();
			int endIndex   = Math.min(startIndex + cfg.perPage(), items.size());

			multi.addItems(inv, items, startIndex, endIndex, staticItemsAllowed, fill, staticItems);
			InventoryUtil.createBoarder(inv, fill);
			// if there is a static items column, then create a line at column 2
			if (staticItemsAllowed) InventoryUtil.verticalLine(inv, fill, 2, true);

			multi.addPage(inv);
		}

		// add the navigation buttons
		var linkedInventories = multi.getLinkedInventories();

		int size      = linkedInventories.getSize();
		int pageIndex = 0;

		String originalTitle = multi.getDisplayTitle();

		for (LinkedList.Node<InventoryHandler> node : linkedInventories) {
			InventoryHandler currentInv = node.getData();
			InventoryHandler nextInv    = node.getNext() == null ? null : node.getNext().getData();

			addNavigationButtons(plugin, currentInv, nextInv, originalTitle, pageIndex, size, buttonTags,
								 p -> multi.nextPage().open(p), p -> multi.previousPage().open(p),
								 p -> multi.homePage().open(p));

			pageIndex++;
		}

		return multi;
	}

	static PageConfig computeConfigForCreation(int itemsCount, boolean staticItemsAllowed, boolean fixedSize) {
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

	static PageConfig computeConfigForUpdate(int itemsCount, int inventorySize) {
		int maxRows         = 4;
		int maxColumns      = inventorySize / 9;
		int perPage         = maxColumns * maxRows;
		int pages           = (int) Math.ceil((double) itemsCount / perPage);
		int remainingAmount = itemsCount % perPage;
		int finalPage       = remainingAmount + 9 * 2 + (int) Math.ceil((double) remainingAmount / 9);
		int initialPage     = pages == 1 ? finalPage : InventoryHandler.MAX_SLOTS;
		return new PageConfig(maxRows, maxColumns, perPage, pages, remainingAmount, finalPage, initialPage);
	}

}
