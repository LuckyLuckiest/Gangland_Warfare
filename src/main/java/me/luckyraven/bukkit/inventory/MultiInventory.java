package me.luckyraven.bukkit.inventory;

import me.luckyraven.bukkit.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
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

	public void addPage(Player player, Inventory gui) {
		Inventory lastPage = inventories.getLast();

		// next page -> gui
		addNextPageItem(player, lastPage);
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

	public List<Inventory> getInventories() {
		return new LinkedList<>(inventories);
	}

}
