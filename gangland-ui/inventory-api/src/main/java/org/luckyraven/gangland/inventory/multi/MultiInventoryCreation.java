package org.luckyraven.gangland.inventory.multi;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.datastructure.LinkedList;
import org.luckyraven.gangland.inventory.InventoryHandler;
import org.luckyraven.gangland.inventory.part.ButtonTags;
import org.luckyraven.gangland.inventory.part.Fill;
import org.luckyraven.gangland.inventory.part.PageConfig;
import org.luckyraven.gangland.inventory.util.InventoryUtil;

import java.util.List;
import java.util.Map;

import static org.luckyraven.gangland.inventory.multi.MultiInventoryNavigation.addNavigationButtons;
import static org.luckyraven.gangland.inventory.util.InventoryUtil.titleRefactor;

public class MultiInventoryCreation {

	@Nullable
	public static MultiInventory dynamicMultiInventory(JavaPlugin plugin, Player player, List<ListEntry> items,
	                                                   String title, boolean staticItemsAllowed, int size,
	                                                   Fill fill, ButtonTags buttonTags,
	                                                   @Nullable Map<Integer, StaticSlotEntry> staticItems) {
		if (staticItemsAllowed) {
			if (staticItems == null || staticItems.isEmpty() || staticItems.size() > 6) return null;
		}

		int        staticCount = staticItems == null ? 0 : staticItems.size();
		PageConfig cfg         = computeConfigForCreation(items.size(), size, staticCount, staticItemsAllowed);

		// the first page
		MultiInventory multi = new MultiInventory(plugin, title, cfg.initialPage(), player);

		int firstPageEnd = Math.min(cfg.perPage(), items.size());
		multi.addItems(multi, items, 0, firstPageEnd, staticItemsAllowed, staticItems);
		InventoryUtil.createBoarder(multi, fill);
		// if there is a static items column, then create a line at column 2
		if (staticItemsAllowed) InventoryUtil.verticalLine(multi, fill, 2, true);

		// the other pages
		int totalPages = cfg.pages();

		for (int page = 1; page < totalPages; page++) {
			int  pageSize = cfg.initialPage();
			long id       = MultiInventory.ID;

			String format          = String.format("%s_%d", title, id);
			String titleRefactored = titleRefactor(format);

			MultiInventory.ID = id + 1;

			InventoryHandler inv = new InventoryHandler(plugin, titleRefactored, pageSize, player);

			int startIndex = page * cfg.perPage();
			int endIndex   = Math.min(startIndex + cfg.perPage(), items.size());

			multi.addItems(inv, items, startIndex, endIndex, staticItemsAllowed, staticItems);
			InventoryUtil.createBoarder(inv, fill);
			// if there is a static items column, then create a line at column 2
			if (staticItemsAllowed) InventoryUtil.verticalLine(inv, fill, 2, true);

			multi.addPage(inv);
		}

		// add the navigation buttons
		var linkedInventories = multi.getLinkedInventories();

		int pageCount = linkedInventories.getSize();
		int pageIndex = 0;

		String originalTitle = multi.getDisplayTitle();

		for (LinkedList.Node<InventoryHandler> node : linkedInventories) {
			InventoryHandler currentInv = node.getData();
			InventoryHandler nextInv    = node.getNext() == null ? null : node.getNext().getData();

			addNavigationButtons(plugin, currentInv, nextInv, originalTitle, pageIndex, pageCount, buttonTags,
			                     p -> multi.nextPage().open(p), p -> multi.previousPage().open(p),
			                     p -> multi.homePage().open(p));

			pageIndex++;
		}

		return multi;
	}

	/**
	 * Determines the per-page layout. Priority resolves Row count:
	 * <ol>
	 *     <li>the explicit {@code size} from YAML (size / 9 rows)</li>
	 *     <li>{@code staticItemCount + 2} border rows when static items are present</li>
	 *     <li>{@code ceil(itemsCount / maxColumns) + 2} border rows as a last resort</li>
	 * </ol>
	 * Rows are clamped to [3, 6]. Pages are always full-size (rows × 9).
	 */
	static PageConfig computeConfigForCreation(int itemsCount, int size, int staticItemCount,
	                                           boolean staticItemsAllowed) {
		int maxColumns = staticItemsAllowed ? 6 : 7;

		int rows;
		if (size >= 18 && size % 9 == 0) {
			rows = size / 9;
		} else if (staticItemsAllowed && staticItemCount > 0) {
			rows = staticItemCount + 2;
		} else {
			rows = (int) Math.ceil((double) Math.max(itemsCount, 1) / maxColumns) + 2;
		}
		rows = Math.max(3, Math.min(rows, 6));

		int maxRows         = Math.max(1, rows - 2);
		int perPage         = maxRows * maxColumns;
		int pages           = Math.max(1, (int) Math.ceil((double) itemsCount / perPage));
		int pageSlots       = rows * 9;
		int remainingAmount = itemsCount - (pages - 1) * perPage;

		return new PageConfig(maxRows, maxColumns, perPage, pages, remainingAmount, pageSlots, pageSlots);
	}

	static PageConfig computeConfigForUpdate(int itemsCount, int inventorySize) {
		int rows       = Math.max(3, Math.min(inventorySize / 9, 6));
		int maxRows    = Math.max(1, rows - 2);
		int maxColumns = 6;
		int perPage    = maxRows * maxColumns;

		int pages           = Math.max(1, (int) Math.ceil((double) itemsCount / perPage));
		int pageSlots       = rows * 9;
		int remainingAmount = itemsCount - (pages - 1) * perPage;

		return new PageConfig(maxRows, maxColumns, perPage, pages, remainingAmount, pageSlots, pageSlots);
	}

}
