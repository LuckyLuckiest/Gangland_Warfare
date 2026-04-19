package me.luckyraven.file;

import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.file.configuration.YamlMessageProvider;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.config.ConfigParser;
import me.luckyraven.persistence.config.ConfigReport;
import me.luckyraven.util.TimeMessages;
import me.luckyraven.util.UnhandledError;
import me.luckyraven.util.autowire.bean.BeanLifecycle;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@CustomLog
public class LanguageLoader implements BeanLifecycle {

	private final Gangland    gangland;
	private final FileManager fileManager;

	private @Getter YamlConfiguration message;

	public LanguageLoader(Gangland gangland, FileManager fileManager) {
		this.gangland    = gangland;
		this.fileManager = fileManager;
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (firstLoad) return;
		initialize();
		Messages.init(new YamlMessageProvider(message));
		TimeMessages.initialize();
	}

	public void initialize() {
		try {
			message = loadMessage(fileManager);
			reportSyntaxErrors();
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

	private YamlConfiguration loadMessage(FileManager manager) throws IOException, InvalidConfigurationException {
		String lang    = Settings.getLanguagePicked();
		String fileLoc = Path.of("message", "message_" + lang + ".yml").toString();

		return manager.loadFromResources(fileLoc);
	}

	/**
	 * Positional pass over the same message file. Bukkit's {@code YamlConfiguration.load} already threw on structural
	 * problems by the time we get here; this pass picks up softer YAML-lint findings (duplicate keys, etc.) with
	 * {@code message_<lang>.yml:L:C} locations so translators know where to look.
	 */
	private void reportSyntaxErrors() {
		String lang     = Settings.getLanguagePicked();
		String fileLoc  = Path.of("message", "message_" + lang + ".yml").toString();
		File   diskFile = new File(gangland.getDataFolder().getAbsolutePath(), fileLoc);

		try (InputStream inputStream = diskFile.exists()
		                               ? new FileInputStream(diskFile)
		                               : gangland.getResource(fileLoc.replace(File.separator, "/"))) {

			if (inputStream == null) return;

			ConfigReport report = new ConfigReport();

			try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
				new ConfigParser().parse(diskFile.toPath(), reader, report);
			}

			if (!report.isEmpty()) report.log(log);
		} catch (IOException ignored) {
			// Primary load succeeded; a secondary IO failure here is non-fatal.
		}
	}

}
