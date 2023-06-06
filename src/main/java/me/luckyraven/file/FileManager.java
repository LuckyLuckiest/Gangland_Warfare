package me.luckyraven.file;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileManager {

	private @Getter
	final         List<FileHandler> files;
	private final JavaPlugin        plugin;

	public FileManager(JavaPlugin plugin) {
		files = new ArrayList<>();
		this.plugin = plugin;
	}

	public void addFile(FileHandler file) {
		files.add(file);
		try {
			file.create();
		} catch (IOException exception) {
			plugin.getLogger().warning(
					String.format("Unable to create file %s.%s, reason: %s", file.getName(), file.getFileType(),
					              exception.getMessage()));
		}
	}

	public FileHandler getFile(String fileName) {
		for (FileHandler file : files) if (file.getName().equalsIgnoreCase(fileName)) return file;
		return null;
	}

	public boolean filesLoaded() {
		for (FileHandler file : files) if (!file.isLoaded()) return false;
		return true;
	}

}
