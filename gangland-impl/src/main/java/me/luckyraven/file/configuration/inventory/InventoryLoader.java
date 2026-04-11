package me.luckyraven.file.configuration.inventory;

import me.luckyraven.Gangland;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.FolderLoader;

public class InventoryLoader extends FolderLoader {

	private final FileManager             fileManager;
	private final InventoryRuntimeContext runtimeContext;

	public InventoryLoader(Gangland gangland, FileManager fileManager, InventoryRuntimeContext runtimeContext) {
		super(gangland, "inventory", fileManager);
		this.fileManager    = fileManager;
		this.runtimeContext = runtimeContext;
	}

	@Override
	public void initialize() {
		this.load(true, runtimeContext::registerInventory, fileManager);
	}

}
