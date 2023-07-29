package me.luckyraven.bukkit.inventory;

import me.luckyraven.bukkit.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedList;
import java.util.List;

public class MultiInventory extends Inventory {

	private final LinkedList<Inventory> inventories;
	private       int                   currentPage;

	public MultiInventory(JavaPlugin plugin, String title, int size) {
		super(plugin, title, size);
		this.inventories = new LinkedList<>();
		this.currentPage = 0;

		this.inventories.add(this);
	}

	public static MultiInventory dynamicMultiInventory(JavaPlugin plugin, List<ItemStack> items, String name,
	                                                   Player player) {
		int maxRows    = 4;
		int maxColumns = 7;
		int amount     = items.size();

		int perPage = maxColumns * maxRows;
		int pages   = (int) Math.ceil((double) amount / perPage);

		int remainingAmount = amount % perPage;
		int finalPage       = remainingAmount + 9 * 2 + (int) Math.ceil((double) remainingAmount / 9);
		int initialPage     = pages == 1 ? finalPage : Inventory.MAX_SLOTS;

		String modifiedName = name + "&r (%d/%d)";
		// the first page
		MultiInventory multi = new MultiInventory(plugin, java.lang.String.format(modifiedName, 1, pages), initialPage);

		int row    = 2;
		int column = 2;
		for (int i = 0; i < items.size() && row % 6 != 0; i++) {
			multi.setItem((row - 1) * 9 + (column - 1), items.get(i), false);

			if (column % 8 == 0) {
				column = 2;
				++row;
			} else ++column;
		}
		multi.createBoarder();

		// the other pages

		// the inventory size of the other pages is determined according to which page is reached
		// it can be that the page reached is the final page that means the finalPage calculation is
		// applied to it

		// need to fill the other pages
		for (int i = 1; i < pages; i++) {
			int size = i == pages - 1 ? finalPage : initialPage;

			Inventory inv = new Inventory(plugin, java.lang.String.format(modifiedName, i + 1, pages), size);

			row = 2;
			column = 2;
			int startIndex = i * perPage;
			int endIndex   = Math.min(startIndex + perPage, items.size());
			for (int j = startIndex; j < endIndex && row % 6 != 0; j++) {
				inv.setItem((row - 1) * 9 + (column - 1), items.get(j), false);

				if (column % 8 == 0) {
					column = 2;
					++row;
				} else ++column;
			}
			inv.createBoarder();
			multi.addPage(player, inv);
		}

		return multi;
	}

	public void addPage(Player player, Inventory gui) {
		Inventory lastPage = inventories.getLast();

		// next page -> gui
		addNextPageItem(player, lastPage);
		// home page
		addHomePageItem(player, gui);
		// prev page -> lastPage
		addPreviousPageItem(player, gui);

		inventories.addLast(gui);
	}

	public boolean removePage(Inventory gui) {
		int current = inventories.lastIndexOf(gui);
		if (current == -1) return false;

		int next = current + 1, prev = current - 1;

		if (current == 0) {
			if (inventories.size() > 1) {
				Inventory nextInventory = inventories.get(next);
				nextInventory.removeItem(nextInventory.getSize() - 9);
			}
		} else if (current == inventories.size() - 1) {
			Inventory prevInventory = inventories.get(prev);
			prevInventory.removeItem(prevInventory.getSize() - 1);
		}

		inventories.remove(current);
		return true;
	}

	public Inventory nextPage() {
		currentPage++;
		if (currentPage >= inventories.size()) currentPage = inventories.size() - 1;
		return inventories.get(currentPage);
	}

	public Inventory previousPage() {
		currentPage--;
		if (currentPage < 0) currentPage = 0;
		return inventories.get(currentPage);
	}

	public boolean hasNextPage() {
		return currentPage < inventories.size();
	}

	public Inventory homePage() {
		currentPage = 0;
		return inventories.getFirst();
	}

	private void addNextPageItem(Player player, Inventory linkedInventory) {
		ItemBuilder item  = new ItemBuilder(Material.PLAYER_HEAD).setDisplayName("&a->");
		String      arrow = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTYzMzlmZjJlNTM0MmJhMThiZGM0OGE5OWNjYTY1ZDEyM2NlNzgxZDg3ODI3MmY5ZDk2NGVhZDNiOGFkMzcwIn19fQ==";

		item.customHead(arrow);
		linkedInventory.setItem(linkedInventory.getSize() - 1, item.build(), false, (inventory, itemBuilder) -> {
			// close the old inventory
			linkedInventory.close(player);

			// open the new inventory
			nextPage().open(player);
		});
	}

	private void addPreviousPageItem(Player player, Inventory linkedInventory) {
		ItemBuilder item  = new ItemBuilder(Material.PLAYER_HEAD).setDisplayName("&c<-");
		String      arrow = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjg0ZjU5NzEzMWJiZTI1ZGMwNThhZjg4OGNiMjk4MzFmNzk1OTliYzY3Yzk1YzgwMjkyNWNlNGFmYmEzMzJmYyJ9fX0=";

		item.customHead(arrow);
		linkedInventory.setItem(linkedInventory.getSize() - 9, item.build(), false, (inventory, itemBuilder) -> {
			// close the old inventory
			linkedInventory.close(player);

			// open the new inventory
			previousPage().open(player);
		});
	}

	private void addHomePageItem(Player player, Inventory linkedInventory) {
		ItemBuilder item = new ItemBuilder(Material.PLAYER_HEAD).setDisplayName(
				"&cBack to " + this.getTitle().getKey());
		String arrow = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjg0ZjU5NzEzMWJiZTI1ZGMwNThhZjg4OGNiMjk4MzFmNzk1OTliYzY3Yzk1YzgwMjkyNWNlNGFmYmEzMzJmYyJ9fX0=";

		item.customHead(arrow);
		linkedInventory.setItem(linkedInventory.getSize() - 5, item.build(), false, (inventory, itemBuilder) -> {
			// close the old inventory
			linkedInventory.close(player);

			// open the new inventory
			homePage().open(player);
		});
	}

	public List<Inventory> getInventories() {
		return new LinkedList<>(inventories);
	}

}
