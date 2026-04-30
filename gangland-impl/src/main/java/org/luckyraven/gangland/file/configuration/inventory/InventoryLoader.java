package org.luckyraven.gangland.file.configuration.inventory;

import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.persistence.FileManager;
import org.luckyraven.gangland.persistence.FolderLoader;

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

	@Override
	public void onInitialize(boolean firstLoad) {
		if (firstLoad) return;
		initialize();
	}
}
