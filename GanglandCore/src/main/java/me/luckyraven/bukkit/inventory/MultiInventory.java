package me.luckyraven.bukkit.inventory;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XSound;
import com.google.common.base.Preconditions;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.data.user.User;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.InventoryUtil;
import me.luckyraven.util.TriConsumer;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MultiInventory extends InventoryHandler {

	private static int                          ID = 0;
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

	public static MultiInventory dynamicMultiInventory(Gangland gangland, User<Player> user, List<ItemStack> items,
													   String name, boolean staticItemsAllowed, boolean fixedSize,
													   @Nullable
													   Map<ItemStack, TriConsumer<Player, InventoryHandler, ItemBuilder>> staticItems) {
		if (staticItemsAllowed) {
			Preconditions.checkNotNull(staticItems, "No static items set");
			Preconditions.checkArgument(staticItems.size() <= 6, "Can't add more items than max rows");
		}

		int maxRows    = 4;
		int maxColumns = staticItemsAllowed ? 6 : 7;
		int amount     = items.size();

		int perPage = maxColumns * maxRows;
		int pages   = (int) Math.ceil((double) amount / perPage);

		int remainingAmount = amount % perPage;
		// the inventory size of the other pages is determined according to which page is reached
		// it can be that the page reached is the final page that means the finalPage calculation is
		// applied to it
		int finalPage   = remainingAmount + 9 * 2 + (int) Math.ceil((double) remainingAmount / 9);
		int initialPage = pages == 1 ? finalPage : InventoryHandler.MAX_SLOTS;

		if (fixedSize) {
			finalPage   = InventoryHandler.MAX_SLOTS;
			initialPage = InventoryHandler.MAX_SLOTS;
		}

		// the first page
		MultiInventory multi = new MultiInventory(gangland, name, initialPage, user);

		multi.addItems(multi, items, 0, items.size(), staticItemsAllowed, staticItems);
		InventoryUtil.createBoarder(multi);
		// if there is static items column, then create a line at column 2
		if (staticItemsAllowed) InventoryUtil.verticalLine(multi, 2, InventoryUtil.getFillItem(),
														   SettingAddon.getInventoryFillName(), true);

		// the other pages
		for (int i = 1; i < pages; i++) {
			int size = i == pages - 1 ? finalPage : initialPage;

			NamespacedKey    key = new NamespacedKey(gangland, titleRefactor(String.format("%s_%d", name, ++ID)));
			InventoryHandler inv = new InventoryHandler(gangland, name, size, user, key, false);

			int startIndex = i * perPage;
			int endIndex   = Math.min(startIndex + perPage, items.size());

			multi.addItems(inv, items, startIndex, endIndex, staticItemsAllowed, staticItems);
			InventoryUtil.createBoarder(inv);
			// if there is static items column, then create a line at column 2
			if (staticItemsAllowed) InventoryUtil.verticalLine(inv, 2, InventoryUtil.getFillItem(),
															   SettingAddon.getInventoryFillName(), true);

			multi.addPage(inv);
		}

		return multi;
	}

	public void updateItems(List<ItemStack> items, User<Player> user, boolean staticItemsAllowed,
							@Nullable Map<ItemStack, TriConsumer<Player, InventoryHandler, ItemBuilder>> staticItems) {
		if (inventories.isEmpty()) {
			return; // No inventories to update
		}

		int maxRows    = 4;
		int maxColumns = inventories.getFirst().getSize() / 9;

		int perPage = maxColumns * maxRows;
		int pages   = (int) Math.ceil((double) items.size() / perPage);

		int remainingAmount = items.size() % perPage;
		int finalPage       = remainingAmount + 9 * 2 + (int) Math.ceil((double) remainingAmount / 9);
		int initialPage     = pages == 1 ? finalPage : InventoryHandler.MAX_SLOTS;

		int              inventoryIndex = 0;
		InventoryHandler firstPage      = inventories.getFirst();

		// Update the first page with new items
		firstPage.clear();

		addItems(firstPage, items, 0, Math.min(perPage, items.size()), staticItemsAllowed, staticItems);
		InventoryUtil.createBoarder(firstPage);
		if (staticItemsAllowed) InventoryUtil.verticalLine(firstPage, 2, InventoryUtil.getFillItem(),
														   SettingAddon.getInventoryFillName(), true);

		for (int i = 1; i < pages; i++) {
			int              size = i == pages - 1 ? finalPage : initialPage;
			InventoryHandler inv;

			if (i >= inventories.size()) {
				// If there's no corresponding inventory for this page, create a new one
				NamespacedKey namespacedKey = new NamespacedKey(gangland, titleRefactor(
						String.format("%s_%d", firstPage.getDisplayTitle(), ++ID)));
				inv = new InventoryHandler(gangland, firstPage.getDisplayTitle(), size, user, namespacedKey, false);
				addItems(inv, items, i * perPage, Math.min((i + 1) * perPage, items.size()), staticItemsAllowed,
						 staticItems);
				addPage(inv);
			} else {
				// If there's already an inventory for this page, update its items and title
				inv = inventories.get(i);
				inv.clear();
				addItems(inv, items, i * perPage, Math.min((i + 1) * perPage, items.size()), false, null);
			}
			InventoryUtil.createBoarder(inv);
			if (staticItemsAllowed) InventoryUtil.verticalLine(inv, 2, InventoryUtil.getFillItem(),
															   SettingAddon.getInventoryFillName(), true);

			inventoryIndex++;
		}

		// Remove any extra inventories if there were more pages before the update
		while (inventories.size() > inventoryIndex + 1) {
			removePage(inventories.getLast());
		}
	}

	public void addPage(InventoryHandler currentInv) {
		InventoryHandler lastInv = inventories.getLast();

		addNavigationButtons(currentInv, lastInv);

		inventories.addLast(currentInv);
	}

	public boolean removePage(InventoryHandler gui) {
		int current = inventories.lastIndexOf(gui);
		if (current == -1) return false;

		int next = current + 1, prev = current - 1;

		if (current == 0) if (inventories.size() > 1) {
			InventoryHandler nextInventory = inventories.get(next);
			nextInventory.removeItem(nextInventory.getSize() - 9);
		} else if (current == inventories.size() - 1) {
			InventoryHandler prevInventory = inventories.get(prev);
			prevInventory.removeItem(prevInventory.getSize() - 1);
		}

		inventories.remove(current);
		return true;
	}

	public InventoryHandler nextPage() {
		++currentPage;
		if (currentPage >= inventories.size()) currentPage = inventories.size() - 1;
		return inventories.get(currentPage);
	}

	public InventoryHandler previousPage() {
		--currentPage;
		if (currentPage < 0) currentPage = 0;
		return inventories.get(currentPage);
	}

	public boolean hasNextPage() {
		return currentPage < inventories.size();
	}

	public InventoryHandler homePage() {
		currentPage = 0;
		return inventories.getFirst();
	}

	public List<InventoryHandler> getLinkedInventories() {
		return new LinkedList<>(inventories);
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

	private void addNextPageItem(InventoryHandler linkedInventory) {
		ItemBuilder item = createItemHead("&a->", String.format("&7(%d/%d)", currentPage + 1, inventories.size()));
		String arrow
				= "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTYzMzlmZjJlNTM0MmJhMThiZGM0OGE5OWNjYTY1ZDEyM2NlNzgxZDg3ODI3MmY5ZDk2NGVhZDNiOGFkMzcwIn19fQ==";

		item.customHead(arrow);
		linkedInventory.setItem(linkedInventory.getSize() - 1, item.build(), false,
								(player, inventory, itemBuilder) -> {
									// open the next inventory
									nextPage().open(player);

									buttonClickSound(player);
								});
	}

	private void addPreviousPageItem(InventoryHandler linkedInventory) {
		ItemBuilder item = createItemHead("&c<-");
		String arrow
				= "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjg0ZjU5NzEzMWJiZTI1ZGMwNThhZjg4OGNiMjk4MzFmNzk1OTliYzY3Yzk1YzgwMjkyNWNlNGFmYmEzMzJmYyJ9fX0=";

		item.customHead(arrow);
		linkedInventory.setItem(linkedInventory.getSize() - 9, item.build(), false,
								(player, inventory, itemBuilder) -> {
									// open the previous inventory
									previousPage().open(player);

									buttonClickSound(player);
								});
	}

	private void addHomePageItem(InventoryHandler linkedInventory) {
		ItemBuilder item = createItemHead("&cBack to " + this.getDisplayTitle());
		String home
				= "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWUxNTZlYjVhZmZkZGYyMDg2MTdhYWI3YjQzMGZhZDlmMmM5OTFlYzJmMzgzMDRhMGMyMTNmMzFlNzZjYmJlNCJ9fX0=";

		item.customHead(home);
		linkedInventory.setItem(linkedInventory.getSize() - 5, item.build(), false,
								(player, inventory, itemBuilder) -> {
									// open the home inventory
									homePage().open(player);

									buttonClickSound(player);
								});
	}

	private ItemBuilder createItemHead(String name, @Nullable String... lore) {
		ItemBuilder item = new ItemBuilder(XMaterial.PLAYER_HEAD.parseMaterial()).setDisplayName(name);

		if (lore != null) item.setLore(lore);

		return item;
	}

	private void addNavigationButtons(InventoryHandler currentInv, InventoryHandler lastInv) {
		// next page -> gui
		addNextPageItem(lastInv);

		// home page
		addHomePageItem(currentInv);

		// prev page -> lastPage
		addPreviousPageItem(currentInv);
	}

	private void buttonClickSound(Player player) {
		// sound
		Sound sound = XSound.BLOCK_WOODEN_BUTTON_CLICK_ON.parseSound();
		if (sound != null) player.playSound(player.getLocation(), sound, 1F, 1F);
	}

}
