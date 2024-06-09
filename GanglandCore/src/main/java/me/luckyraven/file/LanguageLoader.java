package me.luckyraven.file;

import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.UnhandledError;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class LanguageLoader {

	private final Gangland gangland;

	private @Getter YamlConfiguration message;

	public LanguageLoader(Gangland gangland) {
		this.gangland = gangland;
		try {
			message = loadMessage(gangland.getInitializer().getFileManager());
		} catch (IOException | InvalidConfigurationException exception) {
			this.gangland.getLogger().warning(UnhandledError.FILE_LOADER_ERROR + ": " + exception.getMessage());

			Set<String>   files     = getMessageFiles();
			StringBuilder languages = new StringBuilder();
			String[]      nam       = files.toArray(String[]::new);

			for (int i = 0; i < files.size(); i++) {
				String file = nam[i];
				languages.append(file, file.lastIndexOf("_") + 1, file.lastIndexOf("."));
				if (i < files.size() - 1) languages.append(", ");
			}
			this.gangland.getLogger()
						 .info("Disabling plugin, reason: unidentifiable message file.\nPlease use languages from the list: " +
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
			gangland.getLogger()
					.warning(UnhandledError.MISSING_JAR_ERROR + ": " + exception.getMessage() + "\n" +
							 "This error occurred since the plugin jar file is not in the plugins folder.");
		}
		return files;
	}

	private YamlConfiguration loadMessage(FileManager manager) throws IOException, InvalidConfigurationException {
		String lang    = SettingAddon.getLanguagePicked();
		String fileLoc = "message" + File.separator + "message_" + lang + ".yml";

		return manager.loadFromResources(fileLoc);
	}

}
