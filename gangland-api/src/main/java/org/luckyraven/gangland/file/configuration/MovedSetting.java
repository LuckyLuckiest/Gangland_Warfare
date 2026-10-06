package org.luckyraven.gangland.file.configuration;

import lombok.CustomLog;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/**
 * The 0.15.1 one-release bridge (gangland-api 2.2) for a setting that moved out of the core {@code settings.yml} into a
 * module's own YAML. A module builds one per config load and reads every moved key through it with the key's new path,
 * its old {@code settings.yml} path and its default:
 *
 * <pre>{@code
 * MovedSetting moved = MovedSetting.of(turfFile, fileManager, "turf");
 * int radius = moved.getInt("Capture.Radius", "Turf.Capture_Radius", 16);
 * }</pre>
 *
 * <p>Rule, per key: the module value is the module file's value when set there, else the default. The legacy value
 * is the old path's value in {@code settings.yml}, or, when {@code settings.yml} does not set it, in the newest backup
 * Keystone wrote beside it ({@code settings-old.yml}, {@code settings-old (1).yml}, ...; newest by modification time).
 * The backup matters on upgrade: {@code settings.yml} carries {@code Config_Version: '${project.version}'}, so the
 * version bump makes Keystone move the owner's file to such a backup and write a fresh one without the moved keys
 * before any module reads. The legacy value wins (with one warning per key per instance naming the file it came from
 * and the module file) only when it differs from the default while the module value is still the default, so an owner
 * who tuned the old key keeps the tuning until they copy it over. In every other case the module value wins. A later
 * release stops reading the legacy files and deletes this class.
 *
 * @since gangland-api 2.2
 */
@CustomLog
public final class MovedSetting {

	private final FileHandler moduleFile;
	private final FileManager fileManager;
	private final String      moduleId;
	private final Set<String> warned = new HashSet<>();

	private boolean           backupLoaded;
	private File              backupFile;
	private FileConfiguration backup;

	private MovedSetting(FileHandler moduleFile, FileManager fileManager, String moduleId) {
		this.moduleFile  = moduleFile;
		this.fileManager = fileManager;
		this.moduleId    = moduleId;
	}

	/**
	 * @param moduleFile  the module's own YAML the keys moved to
	 * @param fileManager the host file manager, used to find the core {@code settings} file
	 * @param moduleId    the module id named in the migration warning (e.g. {@code turf})
	 */
	public static MovedSetting of(FileHandler moduleFile, FileManager fileManager, String moduleId) {
		return new MovedSetting(moduleFile, fileManager, moduleId);
	}

	public int getInt(String path, String legacyPath, int def) {
		return read(path, legacyPath, def, FileConfiguration::getInt, Objects::equals);
	}

	public long getLong(String path, String legacyPath, long def) {
		return read(path, legacyPath, def, FileConfiguration::getLong, Objects::equals);
	}

	public double getDouble(String path, String legacyPath, double def) {
		return read(path, legacyPath, def, FileConfiguration::getDouble, Objects::equals);
	}

	public boolean getBoolean(String path, String legacyPath, boolean def) {
		return read(path, legacyPath, def, FileConfiguration::getBoolean, Objects::equals);
	}

	public String getString(String path, String legacyPath, String def) {
		return read(path, legacyPath, def, FileConfiguration::getString, Objects::equals);
	}

	public List<String> getStringList(String path, String legacyPath, List<String> def) {
		return read(path, legacyPath, def, FileConfiguration::getStringList, Objects::equals);
	}

	/**
	 * A money value, parsed like {@code Settings}' money keys ({@link Currency#parse}, from the YAML string so large
	 * literals keep their precision); a malformed value counts as unset. Equality is numeric ({@code compareTo}).
	 *
	 * @param def plain-decimal default, e.g. {@code "100"}
	 */
	public BigDecimal getMoney(String path, String legacyPath, String def) {
		return read(path, legacyPath, Currency.parse(def), MovedSetting::money,
		            (a, b) -> a != null && b != null && a.compareTo(b) == 0);
	}

	private <T> T read(String path, String legacyPath, T def, BiFunction<FileConfiguration, String, T> getter,
	                   BiPredicate<T, T> same) {
		T module = value(config(moduleFile), path, getter);
		if (module == null) module = def;

		FileHandler settings = fileManager.getFile("settings");
		T           legacy   = value(config(settings), legacyPath, getter);
		String      source   = where(settings);

		if (legacy == null) {
			legacy = value(backup(settings), legacyPath, getter);
			source = backupFile == null ? null : backupFile.getPath();
		}
		if (legacy == null || same.test(legacy, def) || !same.test(module, def)) return module;

		if (warned.add(legacyPath)) {
			log.warn("{} still sets '{}', which moved to {} (owned by the {} module); using that value for now. Copy " +
			         "it there (and delete it from settings.yml if it is still there); the legacy value stops being " +
			         "read in a later release.", source, legacyPath, where(moduleFile), moduleId);
		}
		return legacy;
	}

	/**
	 * The newest {@code <name>-old[ (n)].<type>} Keystone left beside {@code settings}, loaded once; {@code null} when
	 * there is none or it does not parse.
	 */
	private FileConfiguration backup(FileHandler settings) {
		if (backupLoaded) return backup;
		backupLoaded = true;

		File file = settings == null ? null : settings.getFile();
		File[] candidates = file == null || file.getParentFile() == null ? null : file.getParentFile().listFiles();
		if (candidates == null) return null;

		Pattern name = Pattern.compile(Pattern.quote(settings.getName() + "-old") + "( \\(\\d+\\))?" +
		                               Pattern.quote("." + settings.getFileType()));
		backupFile = Arrays.stream(candidates)
		                   .filter(f -> f.isFile() && name.matcher(f.getName()).matches())
		                   .max(Comparator.comparingLong(File::lastModified))
		                   .orElse(null);
		if (backupFile == null) return null;

		try {
			YamlConfiguration config = new YamlConfiguration();
			config.load(backupFile);
			backup = config;
		} catch (IOException | InvalidConfigurationException | RuntimeException e) {
			log.warn("Could not read {} for moved settings ({}); ignoring it.", backupFile.getPath(), e.getMessage());
			backupFile = null;
		}
		return backup;
	}

	private static <T> T value(FileConfiguration config, String path, BiFunction<FileConfiguration, String, T> getter) {
		if (config == null || !config.isSet(path)) return null;

		return getter.apply(config, path);
	}

	private static BigDecimal money(FileConfiguration config, String path) {
		try {
			return Currency.parse(config.getString(path));
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static FileConfiguration config(FileHandler file) {
		return file == null ? null : file.getFileConfiguration();
	}

	/** The file's path as Keystone resolved it (inside the plugin data folder), else its name. */
	private static String where(FileHandler file) {
		if (file == null) return null;

		return file.getFile() == null ? file.getName() : file.getFile().getPath();
	}
}
