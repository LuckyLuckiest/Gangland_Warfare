package me.luckyraven.file.configuration.inventory;

import me.luckyraven.Gangland;
import me.luckyraven.file.FileHandler;
import me.luckyraven.file.FileManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class InventoryLoader {

	private final List<FileHandler> inventoryFiles;

	public InventoryLoader(Gangland gangland) {
		this.inventoryFiles = new ArrayList<>();

		// check if the inventory folder is available
		File   folder = new File(gangland.getDataFolder(), "inventory");
		File[] files  = folder.listFiles();

		if (!folder.exists() || files == null || files.length == 0) {
			gangland.getLogger().info("Inventory folder either is empty or doesn't exist. Skipping...");
			return;
		}

		FileManager fileManager = gangland.getInitializer().getFileManager();

		for (File file : files) {
			try {
				FileHandler fileHandler = new FileHandler(gangland, file);

				fileManager.addFile(fileHandler, false);
				inventoryFiles.add(fileHandler);

				InventoryAddon.registerInventory(gangland, fileHandler);
				Gangland.getLog4jLogger().info("Registered inventory " + file.getName());
			} catch (IOException exception) {
				Gangland.getLog4jLogger().error("There was a problem loading " + file.getName(), exception);
			} catch (Exception exception) {
				Gangland.getLog4jLogger().error("There was a problem registering the inventory " + file.getName(),
				                                exception);
			}
		}

	}

	public List<FileHandler> getInventoryFiles() {
		return new ArrayList<>(inventoryFiles);
	}

}
