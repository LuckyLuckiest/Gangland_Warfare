package org.luckyraven.gangland.file.configuration.inventory;

import lombok.CustomLog;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.FolderLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

@CustomLog
public class InventoryLoader extends FolderLoader {

	private final Gangland                gangland;
	private final FileManager             fileManager;
	private final InventoryRuntimeContext runtimeContext;

	public InventoryLoader(Gangland gangland, FileManager fileManager, InventoryRuntimeContext runtimeContext) {
		super(gangland, "inventory", fileManager);
		this.gangland       = gangland;
		this.fileManager    = fileManager;
		this.runtimeContext = runtimeContext;
	}

	@Override
	public void initialize() {
		this.load(true, handler -> {
			refreshIfUnversioned(handler);
			runtimeContext.registerInventory(handler);
		}, fileManager);
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (firstLoad) return;
		initialize();
	}

	/**
	 * T-263: a shipped menu written before 0.17.0 has no Config_Version, so Keystone never refreshed it. Replace it once
	 * from the jar (Keystone keeps the old file as <name>-old.yml). A menu with no copy in the jar is the owner's own and
	 * is left alone.
	 */
	void refreshIfUnversioned(FileHandler handler) {
		if (handler.getFileConfiguration().getString("Config_Version") != null) {
			return;
		}
		String resource = "inventory/" + handler.getName() + ".yml";
		if (!isShippedMenu(handler, resource)) {
			return;
		}
		try {
			handler.createNewFile();   // moves the old file to -old and copies the jar version
			handler.reloadData();      // re-parses so registerInventory reads the new file
		} catch (IOException exception) {
			log.warn("Could not refresh {} from the jar: {}", resource, exception.getMessage());
		}
	}

	/** True only for the top-level {@code inventory/<name>.yml} the jar ships; a nested file with that name is not. */
	private boolean isShippedMenu(FileHandler handler, String resource) {
		Path expected = new File(gangland.getDataFolder(), resource).toPath().toAbsolutePath().normalize();
		if (!handler.getFile().toPath().toAbsolutePath().normalize().equals(expected)) {
			return false;
		}
		try (InputStream jarCopy = gangland.getResource(resource)) {
			return jarCopy != null;
		} catch (IOException exception) {
			return false;
		}
	}
}
