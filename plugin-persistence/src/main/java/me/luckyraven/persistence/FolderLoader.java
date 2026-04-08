package me.luckyraven.persistence;

import lombok.CustomLog;
import me.luckyraven.util.UnhandledError;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

@CustomLog
public abstract class FolderLoader extends FileLoader<FileHandler> {

	private final JavaPlugin plugin;

	private final String            folder;
	private final List<FileHandler> folderFiles;
	private final List<FileHandler> expectedFolderFiles;

	public FolderLoader(JavaPlugin plugin, String folder) {
		super(plugin);

		this.plugin              = plugin;
		this.folder              = folder;
		this.folderFiles         = new ArrayList<>();
		this.expectedFolderFiles = new ArrayList<>();
	}

	@Override
	public abstract void initialize();

	public void addFile(FileHandler fileHandler) {
		folderFiles.add(fileHandler);
	}

	public void addExpectedFile(FileHandler fileHandler) {
		if (expectedFolderFiles.contains(fileHandler)) return;

		expectedFolderFiles.add(fileHandler);
	}

	public String getFolderName() {
		return folder.substring(folder.lastIndexOf("/") + 1);
	}

	public List<FileHandler> getFiles() {
		return Collections.unmodifiableList(folderFiles);
	}

	@Override
	public void clear() {
		folderFiles.clear();
	}

	/**
	 * Folder loaders own many files; the orchestrator's regenerate-on-failure path doesn't apply here, so we return
	 * {@code null} and let {@link FileManager} skip recovery if a folder loader is ever registered with it. Per-file
	 * errors are already handled inside {@link #loadData(Consumer, FileManager)}.
	 */
	@Override
	protected FileHandler resolvePrimaryHandler(FileManager fileManager) {
		return null;
	}

	@Override
	protected void loadData(Consumer<FileHandler> consumer, FileManager fileManager) {
		// check if the folder is available
		File   folder = new File(plugin.getDataFolder(), this.folder);
		File[] files  = folder.listFiles();

		if (!folder.exists() || files == null || files.length == 0) {
			log.info("No '{}' files were found... Creating new ones.", getFolderName());

			// add files to the folder files if they weren't already added
			if (files != null) addFiles(files);
			else folderFiles.addAll(expectedFolderFiles);

			// when the folder files are empty, then don't create any
			if (folderFiles.isEmpty()) return;

			// create each file if not present
			createFiles(folderFiles);
		}
		// check the folder with the contents available and add them
		else {
			addFiles(files);

			// when the folder files are empty, then don't create any
			if (folderFiles.isEmpty()) return;
		}

		// add each file handler from the folder to the file manager
		List<String> temp = new ArrayList<>();
		for (FileHandler fileHandler : folderFiles) {
			try {
				// check if the file is already in the file manager
				if (!fileManager.contains(fileHandler.getName())) fileManager.addFile(fileHandler, true);

				// process each file handler
				consumer.accept(fileHandler);
				temp.add(fileHandler.getName());
			} catch (Exception exception) {
				log.error("{}: There was a problem registering the {} {}", UnhandledError.FILE_LOADER_ERROR,
				          getFolderName(), fileHandler.getName(), exception);
			}
		}

		if (temp.isEmpty()) log.info("No files were handled");
		else {
			log.info("Registered the following files from '{}' folder:", getFolderName());
			log.info(temp);
		}
	}

	private void addFiles(File[] files) {
		for (File file : files) {
			try {
				FileHandler temp = new FileHandler(plugin, file);
				// check if the file wasn't added, then add it
				if (!folderFiles.contains(temp)) addFile(temp);
			} catch (IOException exception) {
				log.error("{}: There was a problem with loading the file {}.", UnhandledError.FILE_CREATE_ERROR,
				          file.getName(), exception);
			}
		}
	}

	private void createFiles(List<FileHandler> files) {
		try {
			for (FileHandler file : files)
				file.create(true);
		} catch (IOException exception) {
			log.info("{}: {}", UnhandledError.FILE_CREATE_ERROR, exception.getMessage(), exception);
		}
	}

}
