package me.luckyraven.file.configuration.inventory;

import me.luckyraven.Gangland;
import me.luckyraven.file.FileHandler;
import me.luckyraven.file.FileManager;
import me.luckyraven.util.UnhandledError;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class InventoryLoader {

	private final Gangland          gangland;
	private final List<FileHandler> inventoryFiles;

	public InventoryLoader(Gangland gangland) {
		this.gangland = gangland;
		this.inventoryFiles = new ArrayList<>();
	}

	public void initialize() {
		// check if the inventory folder is available
		File   folder = new File(gangland.getDataFolder(), "inventory");
		File[] files  = folder.listFiles();

		if (!folder.exists() || files == null || files.length == 0) {
			Gangland.getLog4jLogger().info("No inventory files were found... Creating new ones.");

			if (inventoryFiles.isEmpty()) return;

			createFiles(inventoryFiles);
		}

		FileManager fileManager = gangland.getInitializer().getFileManager();
		for (FileHandler fileHandler : inventoryFiles) {
			try {
				fileManager.addFile(fileHandler, true);

				InventoryAddon.registerInventory(gangland, fileHandler);
				Gangland.getLog4jLogger().info("Registered inventory " + fileHandler.getName());
			} catch (Exception exception) {
				Gangland.getLog4jLogger().error(String.format("%s: There was a problem registering the inventory %s.",
				                                              UnhandledError.FILE_LOADER_ERROR, fileHandler.getName()),
				                                exception);
			}
		}
	}

	public void addInventory(FileHandler fileHandler) {
		inventoryFiles.add(fileHandler);
	}

	private void createFiles(List<FileHandler> files) {
		try {
			for (FileHandler file : files)
				file.create(true);
		} catch (IOException exception) {
			Gangland.getLog4jLogger().info(UnhandledError.FILE_CREATE_ERROR + ": " + exception.getMessage(), exception);
		}
	}

	public List<FileHandler> getInventoryFiles() {
		return new ArrayList<>(inventoryFiles);
	}

}
