package org.luckyraven.gangland.file.configuration;

import org.bukkit.configuration.file.FileConfiguration;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.IOException;
import java.util.List;

/**
 * Base for a module-owned, localized message/config YAML: {@code <baseName>.yml} (English) is always required,
 * {@code <baseName>_<code>.yml} (for the picked language, e.g. {@code _es}, {@code _de}) is optional and read first
 * whenever {@link Settings#getLanguagePicked()} names a language <em>and</em> that file is registered; a key it lacks,
 * or a missing file, falls back to the English file and then to the caller's default, never throwing just because
 * the localized variant is missing. Every read is formatted through {@link GanglandChatUtil} the same way
 * the core {@code Messages} enum does, so module-owned strings render identically to what {@code Messages} used to
 * produce.
 *
 * <p>WS6 G3 extracted this from the mechanism WS3 G4 fix round 1 (W53/F1) proved for
 * {@code gangland-lootchest}'s {@code GanglandLootChestMessages}. {@code CivilianMessages} (this gate's worked
 * example) is the first consumer; {@code GanglandLootChestMessages} and gadget's {@code JetpackMessages} predate
 * this class and keep their own copy of the same logic for now — migrating them onto this base is a follow-up,
 * not this gate.
 *
 * <p>Core's own {@code message_<code>.yml} pair follows the same naming.
 */
public abstract class LocalizedModuleYaml implements FileInitializer {

	private final FileHandler fileHandler;
	private final FileHandler englishHandler;

	protected LocalizedModuleYaml(FileManager fileManager, String baseName) {
		FileHandler english = tryGetFile(fileManager, baseName);
		FileHandler localized = null;

		String language = Settings.getLanguagePicked();
		if (language != null && !language.isBlank() && !"en".equalsIgnoreCase(language)) {
			localized = tryGetFile(fileManager, baseName + "_" + language.toLowerCase());
		}

		if (localized == null && english == null) {
			throw new PluginException("Neither " + baseName + " nor " + baseName + "_<language> is registered");
		}

		this.fileHandler    = localized != null ? localized : english;
		this.englishHandler = english;
	}

	/**
	 * The configuration that answers {@code key}: the picked language's file when it sets the key, else the English
	 * file, else the picked language's file (so the caller's fallback applies).
	 */
	private FileConfiguration configFor(String key) {
		var own = fileHandler.getFileConfiguration();
		if (own.isSet(key) || englishHandler == null || englishHandler == fileHandler) return own;

		var english = englishHandler.getFileConfiguration();
		return english.isSet(key) ? english : own;
	}

	private static FileHandler tryGetFile(FileManager fileManager, String fileName) {
		try {
			fileManager.checkFileLoaded(fileName);
			return fileManager.getFile(fileName);
		} catch (IOException exception) {
			return null;
		}
	}

	@Override
	public final FileHandler getFileHandler() {
		return fileHandler;
	}

	@Override
	public void initialize() {
		// Flat strings read lazily per call — nothing to pre-parse by default; override if a subclass needs to.
	}

	protected final String raw(String key, String fallback) {
		return configFor(key).getString(key, fallback);
	}

	protected final String color(String key, String fallback) {
		return GanglandChatUtil.color(raw(key, fallback));
	}

	protected final String command(String key, String fallback) {
		return GanglandChatUtil.commandMessage(raw(key, fallback));
	}

	protected final String error(String key, String fallback) {
		return GanglandChatUtil.errorMessage(raw(key, fallback));
	}

	protected final String information(String key, String fallback) {
		return GanglandChatUtil.informationMessage(raw(key, fallback));
	}

	protected final String prefix(String key, String fallback) {
		return GanglandChatUtil.prefixMessage(raw(key, fallback));
	}

	/**
	 * A list-valued key, for the "one line at random" shape {@link org.luckyraven.gangland.npc.radio.RadioLines}
	 * consumes. An explicit {@code key: []} means silent — it returns the empty list, not {@code fallback}. A
	 * scalar value ({@code key: "single line"}) is wrapped in a one-element list. A missing key returns
	 * {@code fallback}.
	 */
	protected final List<String> list(String key, List<String> fallback) {
		var config = configFor(key);
		if (!config.isSet(key)) return fallback;
		if (config.isList(key)) return config.getStringList(key);

		String scalar = config.getString(key);
		return scalar != null ? List.of(scalar) : fallback;
	}

}
