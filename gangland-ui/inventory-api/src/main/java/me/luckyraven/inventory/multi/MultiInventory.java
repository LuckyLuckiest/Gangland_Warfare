package me.luckyraven.inventory.multi;

import com.google.common.base.Preconditions;
import lombok.AccessLevel;
import lombok.Getter;
import me.luckyraven.core.datastructure.LinkedList;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.part.PageConfig;
import me.luckyraven.inventory.util.InventoryUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

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

	public void updateItems(JavaPlugin plugin, List<ListEntry> items, Player player, boolean staticItemsAllowed,
	                        Fill fill, @Nullable Map<Integer, StaticSlotEntry> staticItems) {
		if (inventories.isEmpty()) {
			return; // No inventories to update
		}

		PageConfig cfg = computeConfigForUpdate(items.size(), inventories.getHead().getData().getSize());

		int              inventoryIndex = 0;
		InventoryHandler firstPage      = inventories.getHead().getData();

		// Update the first page with new items
		firstPage.clear();

		addItems(firstPage, items, 0, Math.min(cfg.perPage(), items.size()), staticItemsAllowed, staticItems);
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
				         staticItems);
				addPage(inv);
			} else {
				// If there's already an inventory for this page, update its items and title
				inv = inventories.get(i);

				if (inv == null) continue;

				inv.clear();
				addItems(inv, items, startIndex, Math.min((i + 1) * cfg.perPage(), items.size()), false, null);
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

	void addItems(InventoryHandler inv, List<ListEntry> items, int startIndex, int endIndex, boolean staticItemsAllowed,
	              @Nullable Map<Integer, StaticSlotEntry> staticItems) {
		if (staticItemsAllowed) {
			Preconditions.checkNotNull(staticItems, "No static items set");
			Preconditions.checkArgument(staticItems.size() <= 6, "Can't add more items than max rows");
		}

		int additional = staticItemsAllowed ? 1 : 0;
		int row        = 2;
		int column     = 2 + additional;

		// Items live between the top border (row 1) and the bottom border (last row); compute the last usable row from
		// the inventory size so smaller inventories fill fewer rows.
		int maxItemRow = inv.getSize() / 9 - 1;

		if (staticItemsAllowed) placeStaticItems(inv, staticItems);

		for (int i = startIndex; i < endIndex && row <= maxItemRow; i++) {
			ListEntry entry = items.get(i);
			int       slot  = (row - 1) * 9 + (column - 1);
			if (entry.onClick() != null) {
				inv.setItem(slot, entry.item(), false, entry.onClick());
			} else {
				inv.setItem(slot, entry.item(), false);
			}

			if (column % 8 == 0) {
				column = 2 + additional;
				++row;
			} else ++column;
		}
	}

	/**
	 * Places each {@link StaticSlotEntry} at its declared YAML slot. Authors control placement directly via the slot
	 * key — entries are never reshuffled into column-1 order. Unclaimed column-1 slots fall through to
	 * {@link InventoryUtil#createBoarder}.
	 */
	private void placeStaticItems(InventoryHandler inv, Map<Integer, StaticSlotEntry> staticItems) {
		for (Map.Entry<Integer, StaticSlotEntry> entry : staticItems.entrySet()) {
			int             slot = entry.getKey();
			StaticSlotEntry data = entry.getValue();
			if (slot < 0 || slot >= inv.getSize()) continue;
			if (inv.getInventory().getItem(slot) != null) continue;
			inv.setItem(slot, data.item(), false, data.onClick());
		}
	}

}
