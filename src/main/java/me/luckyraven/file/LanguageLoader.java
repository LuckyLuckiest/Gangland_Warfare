package me.luckyraven.file;

import lombok.Getter;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class LanguageLoader {

	private final   JavaPlugin        plugin;
	private @Getter YamlConfiguration message;

	public LanguageLoader(JavaPlugin plugin, FileManager fileManager) {
		this.plugin = plugin;
		try {
			message = loadMessage(fileManager);
		} catch (IOException | InvalidConfigurationException exception) {
			plugin.getLogger().warning("Unhandled error (language loader): " + exception.getMessage());
			Set<String>   files     = getMessageFiles();
			StringBuilder languages = new StringBuilder();
			String[]      nam       = files.toArray(new String[0]);

			for (int i = 0; i < files.size(); i++) {
				String file = nam[i];
				languages.append(file, file.lastIndexOf("_") + 1, file.lastIndexOf("."));
				if (i < files.size() - 1) languages.append(", ");
			}
			plugin.getLogger().info(
					"The plugin will crash when trying to print messages, please use languages from the list: " +
							languages);
		}
	}

	private YamlConfiguration loadMessage(FileManager manager) throws IOException, InvalidConfigurationException {
		FileHandler settings = manager.getFile("settings");
		if (settings.isLoaded()) {
			String lang    = settings.getFileConfiguration().getString("Language");
			String fileLoc = "message\\message_" + lang + ".yml";

			File        file = new File(plugin.getDataFolder().getAbsolutePath() + "\\" + fileLoc);
			InputStream inputStream;

			// Checks for the file in system
			if (plugin.getDataFolder().exists() && file.exists()) inputStream = new FileInputStream(file);
				// Checks for the file in resources
			else inputStream = plugin.getResource(fileLoc.replace("\\", "/"));

			if (inputStream == null) throw new FileNotFoundException(
					String.format("message_%s.yml is not registered!", lang));

			YamlConfiguration yamlConfiguration = new YamlConfiguration();
			yamlConfiguration.load(new InputStreamReader(inputStream));

			return yamlConfiguration;
		} else throw new FileNotFoundException("Settings file is not loaded!");
	}

	public Set<String> getMessageFiles() {
		Set<String> files = new HashSet<>();
		// jar location
		String path = URLDecoder.decode(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().getPath(),
		                                StandardCharsets.UTF_8);

		File   directory = new File(plugin.getDataFolder().getAbsolutePath() + "\\message");
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
			plugin.getLogger().warning("Unexpected error (missing jar file): " + exception.getMessage() + "\n" +
					                           "This error occurred since the plugin jar file is not in the plugins folder.");
		}
		return files;
	}

}
