package me.luckyraven.bukkit.inventory;

import me.luckyraven.Gangland;

import java.util.LinkedList;

public class MultiInventory extends Inventory {

	private final LinkedList<Inventory> guis;
	private       int                   currentPage;

	public MultiInventory(Gangland gangland, String title, int size) {
		super(gangland, title, size);
		this.guis = new LinkedList<>();
		this.currentPage = 0;
	}

	public void addPage(Inventory gui) {
		guis.addLast(gui);
	}

	public boolean removePage(Inventory gui) {
		return guis.removeLastOccurrence(gui);
	}

	public Inventory nextPage() {
		currentPage++;
		if (currentPage >= guis.size()) currentPage = guis.size() - 1;
		return guis.get(currentPage);
	}

	public Inventory previousPage() {
		currentPage--;
		if (currentPage < 0) currentPage = 0;
		return guis.get(currentPage);
	}

	public Inventory homePage() {
		currentPage = 0;
		return guis.getFirst();
	}

}
