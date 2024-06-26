package me.luckyraven.file;

import me.luckyraven.Gangland;
import me.luckyraven.util.UnhandledError;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public abstract class FolderLoader extends DataLoader<FileHandler> {

	private final Gangland gangland;

	private final String            folder;
	private final List<FileHandler> folderFiles;

	public FolderLoader(Gangland gangland, String folder) {
		super(gangland);
		this.gangland    = gangland;
		this.folder      = folder;
		this.folderFiles = new ArrayList<>();
	}

	public abstract void initialize();

	public void addFile(FileHandler fileHandler) {
		folderFiles.add(fileHandler);
	}

	public String getFolderName() {
		return folder.substring(folder.lastIndexOf("/") + 1);
	}

	public List<FileHandler> getFiles() {
		return Collections.unmodifiableList(folderFiles);
	}

	@Override
	protected void loadData(Consumer<FileHandler> consumer) {
		// check if the folder is available
		File   folder = new File(gangland.getDataFolder(), this.folder);
		File[] files  = folder.listFiles();

		if (!folder.exists() || files == null || files.length == 0) {
			Gangland.getLog4jLogger().info("No '{}' files were found... Creating new ones.", getFolderName());

			// add files to the folder files if they weren't already added
			if (files != null) for (File file : files) {
				try {
					FileHandler temp = new FileHandler(gangland, file);
					// check if the file wasn't added, then add it
					if (!folderFiles.contains(temp)) addFile(temp);
				} catch (IOException exception) {
					Gangland.getLog4jLogger()
							.error(String.format("%s: There was a problem with loading the file %s.",
												 UnhandledError.FILE_CREATE_ERROR, file.getName()), exception);
				}
			}

			// when the folder files are empty, then don't create any
			if (folderFiles.isEmpty()) return;

			// create each file if not present
			createFiles(folderFiles);
		}

		// add each file handler from the folder to the file manager
		FileManager  fileManager = gangland.getInitializer().getFileManager();
		List<String> temp        = new ArrayList<>();
		for (FileHandler fileHandler : folderFiles)
			try {
				fileManager.addFile(fileHandler, true);

				// process each file handler
				consumer.accept(fileHandler);
				temp.add(fileHandler.getName());
			} catch (Exception exception) {
				Gangland.getLog4jLogger()
						.error("{}: There was a problem registering the {} {}", UnhandledError.FILE_LOADER_ERROR,
							   getFolderName(), fileHandler.getName(), exception);
			}

		if (temp.isEmpty()) Gangland.getLog4jLogger().info("No files were handled");
		else {
			Gangland.getLog4jLogger().info("Registered the following files from '{}' folder:", getFolderName());
			Gangland.getLog4jLogger().info(temp);
		}
	}

	private void createFiles(List<FileHandler> files) {
		try {
			for (FileHandler file : files)
				file.create(true);
		} catch (IOException exception) {
			Gangland.getLog4jLogger()
					.info("{}: {}", UnhandledError.FILE_CREATE_ERROR, exception.getMessage(), exception);
		}
	}

}
