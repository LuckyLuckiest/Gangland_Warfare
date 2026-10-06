package org.luckyraven.gangland.file.configuration;

import lombok.CustomLog;
import org.bukkit.configuration.file.FileConfiguration;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
 * counts only when {@code settings.yml} still sets the old path. The legacy value wins (with one warning per instance
 * naming both files) only when it differs from the default while the module value is still the default, so an owner
 * who tuned the old key keeps the tuning until they copy it over. In every other case the module value wins. A later
 * release stops reading {@code settings.yml} and deletes this class.
 *
 * @since gangland-api 2.2
 */
@CustomLog
public final class MovedSetting {

	private final FileHandler moduleFile;
	private final FileManager fileManager;
	private final String      moduleId;
	private final Set<String> warned = new HashSet<>();

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
		T module = value(moduleFile, path, getter);
		if (module == null) module = def;

		T legacy = value(fileManager.getFile("settings"), legacyPath, getter);
		if (legacy == null || same.test(legacy, def) || !same.test(module, def)) return module;

		if (warned.add(legacyPath)) {
			log.warn("settings.yml still sets '{}', which moved to plugins/Gangland_Warfare/{} (owned by the {} module); " +
			         "using the settings.yml value for now. Copy it there and delete it from settings.yml; the " +
			         "settings.yml value stops being read in a later release.", legacyPath, where(moduleFile),
			         moduleId);
		}
		return legacy;
	}

	private static <T> T value(FileHandler file, String path, BiFunction<FileConfiguration, String, T> getter) {
		if (file == null) return null;

		FileConfiguration config = file.getFileConfiguration();
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

	private static String where(FileHandler file) {
		String directory = file.getDirectory();
		if (directory == null) return file.getName();

		return directory.replace('\\', '/') + Objects.requireNonNullElse(file.getFileType(), "");
	}
}
