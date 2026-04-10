package me.luckyraven.file.configuration.inventory;

import me.luckyraven.Gangland;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.FolderLoader;

public class InventoryLoader extends FolderLoader {

	private final Gangland    gangland;
	private final FileManager fileManager;

	public InventoryLoader(Gangland gangland, FileManager fileManager) {
		super(gangland, "inventory", fileManager);
		this.gangland    = gangland;
		this.fileManager = fileManager;
	}

	@Override
	public void initialize() {
		this.load(true, fileHandler -> InventoryAddon.registerInventory(gangland, fileHandler), fileManager);
	}

}
