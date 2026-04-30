package org.luckyraven.gangland.file;

import lombok.CustomLog;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.core.UnhandledError;
import org.luckyraven.gangland.core.bean.BeanLifecycle;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.file.configuration.YamlMessageProvider;
import org.luckyraven.gangland.persistence.FileHandler;
import org.luckyraven.gangland.persistence.FileManager;
import org.luckyraven.gangland.persistence.config.ConfigParser;
import org.luckyraven.gangland.util.TimeMessages;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@CustomLog
public class LanguageLoader implements BeanLifecycle {

	private final Gangland    gangland;
	private final FileManager fileManager;

	private @Getter YamlConfiguration message;
	private @Getter YamlConfiguration jarMessage;

	public LanguageLoader(Gangland gangland, FileManager fileManager) {
		this.gangland    = gangland;
		this.fileManager = fileManager;
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (firstLoad) return;
		initialize();
		Messages.init(new YamlMessageProvider(message, jarMessage));
		TimeMessages.initialize();
	}

	public void initialize() {
		try {
			jarMessage = loadJarResource();
			message    = loadMessage();
			validateMessageKeys();
		} catch (IOException | InvalidConfigurationException exception) {
			log.warn("{}: {}", UnhandledError.FILE_LOADER_ERROR, exception.getMessage());

			Set<String>   files     = getMessageFiles();
			StringBuilder languages = new StringBuilder();
			String[]      nam       = files.toArray(String[]::new);

			for (int i = 0; i < files.size(); i++) {
				String file = nam[i];
				languages.append(file, file.lastIndexOf("_") + 1, file.lastIndexOf("."));
				if (i < files.size() - 1) languages.append(", ");
			}

			log.warn("Disabling plugin, reason: unidentifiable message file.\nPlease use languages from the list: {}",
			         languages);
			Bukkit.getServer().getPluginManager().disablePlugin(this.gangland);
		}
	}

	public Set<String> getMessageFiles() {
		Set<String> files = new HashSet<>();
		// jar location
		String path = URLDecoder.decode(
				gangland.getClass().getProtectionDomain().getCodeSource().getLocation().getPath(),
				StandardCharsets.UTF_8);

		File   directory = new File(gangland.getDataFolder().getAbsolutePath(), "message");
		File[] con       = directory.listFiles();

		if (con != null) for (File file : con) {
			String name = file.getName();
			files.add(name.substring(name.lastIndexOf("/") + 1));
		}

		// process message files in resource folder
		try (JarFile jar = new JarFile(path)) {
			Iterator<JarEntry> entries = jar.stream().iterator();
			int                i       = 0;

			while (entries.hasNext()) {
				JarEntry entry = entries.next();

				if (!entry.getName().startsWith("message/")) continue;

				String name = entry.getName();

				if (i++ != 0) files.add(name.substring(name.lastIndexOf("/") + 1));
			}
		} catch (IOException exception) {
			log.error("{}: {}\nThis error occurred since the plugin jar file is not in the plugins folder.",
			          UnhandledError.MISSING_JAR_ERROR, exception.getMessage());
		}
		return files;
	}

	/**
	 * Disk-present files are routed through the standard {@link FileHandler} pipeline so {@code Config_Version}
	 * mismatches regenerate (old file moved to {@code -old}, fresh copy extracted from jar) and the
	 * {@link ConfigParser} positional lint runs. Disk-absent files load from the bundled jar resource in memory only —
	 * no disk copy — so admins never end up with a surprise file they didn't create.
	 */
	private YamlConfiguration loadMessage() throws IOException, InvalidConfigurationException {
		String lang     = Settings.getLanguagePicked();
		String fileName = "message_" + lang;
		String relPath  = Path.of("message", fileName + ".yml").toString();
		File   diskFile = new File(gangland.getDataFolder().getAbsolutePath(), relPath);

		if (diskFile.exists()) {
			YamlConfiguration fromDisk = tryLoadFromDisk(fileName);
			if (fromDisk != null) return fromDisk;
			log.warn("{}: message_{}.yml on disk could not be loaded — falling back to jar resource.",
			         UnhandledError.FILE_LOADER_ERROR, lang);
		}

		return fileManager.loadFromResources(relPath);
	}

	/**
	 * Pure jar-resource read, used as the per-key fallback for {@link YamlMessageProvider}. When the admin's on-disk
	 * file is missing a key (typo, accidental deletion, stale file an older plugin version wrote) the provider falls
	 * through to this copy so the plugin still emits the default string instead of {@code null}.
	 */
	private YamlConfiguration loadJarResource() throws IOException, InvalidConfigurationException {
		String lang    = Settings.getLanguagePicked();
		String jarPath = "message/message_" + lang + ".yml";

		try (InputStream in = gangland.getResource(jarPath)) {
			if (in == null) return null;

			YamlConfiguration yaml = new YamlConfiguration();
			yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
			return yaml;
		}
	}

	/**
	 * Attempts the managed {@link FileHandler} pipeline. Returns {@code null} on any failure so {@link #loadMessage()}
	 * can fall back to the jar-in-memory load instead of disabling the plugin.
	 */
	private YamlConfiguration tryLoadFromDisk(String fileName) {
		try {
			FileHandler handler = fileManager.getFile(fileName);

			if (handler == null) {
				handler = new FileHandler(gangland, fileName, "message", ".yml");
				fileManager.addFile(handler, true);
			} else {
				handler.reloadData();
			}

			if (handler.isLoaded() && handler.getFileConfiguration() instanceof YamlConfiguration yaml) return yaml;
		} catch (IOException exception) {
			log.warn("FileHandler error for {}.yml: {}", fileName, exception.getMessage());
		}

		return null;
	}

	/**
	 * Logs every {@link Messages} enum entry whose path is absent from the loaded configuration so admins see a
	 * concrete list of expected keys their file is missing. Structural lint (duplicate keys, malformed YAML) is handled
	 * by {@link FileHandler#getParseReport()} for disk-present files.
	 */
	private void validateMessageKeys() {
		List<String> missing = Messages.findMissingPaths(message);

		if (missing.isEmpty()) return;

		log.warn("message_{}.yml is missing {} declared key(s):", Settings.getLanguagePicked(), missing.size());
		for (String key : missing) log.warn("  - {}", key);
	}

}
