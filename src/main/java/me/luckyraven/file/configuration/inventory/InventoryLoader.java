package me.luckyraven.file.configuration.inventory;

import me.luckyraven.Gangland;
import me.luckyraven.file.FolderLoader;

public class InventoryLoader extends FolderLoader {

	private final Gangland gangland;

	public InventoryLoader(Gangland gangland) {
		super(gangland, "inventory");
		this.gangland = gangland;
	}

	@Override
	public void initialize() {
		this.load(true, fileHandler -> InventoryAddon.registerInventory(gangland, fileHandler));
	}

}
