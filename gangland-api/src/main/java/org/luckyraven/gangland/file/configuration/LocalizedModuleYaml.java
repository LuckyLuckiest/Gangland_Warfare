package org.luckyraven.gangland.file.configuration;

import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.IOException;

/**
 * Base for a module-owned, localized message/config YAML: {@code <baseName>.yml} (English) is always required,
 * {@code <baseName>_es.yml} (Spanish) is optional and picked instead whenever {@link Settings#getLanguagePicked()}
 * is {@code "es"} <em>and</em> that file is registered — falling back to English otherwise, never throwing just
 * because the localized variant is missing. Every read is formatted through {@link GanglandChatUtil} the same way
 * the core {@code Messages} enum does, so module-owned strings render identically to what {@code Messages} used to
 * produce.
 *
 * <p>WS6 G3 extracted this from the mechanism WS3 G4 fix round 1 (W53/F1) proved for
 * {@code gangland-lootchest}'s {@code GanglandLootChestMessages}. {@code CivilianMessages} (this gate's worked
 * example) is the first consumer; {@code GanglandLootChestMessages} and gadget's {@code JetpackMessages} predate
 * this class and keep their own copy of the same logic for now — migrating them onto this base is a follow-up,
 * not this gate.
 *
 * <p>Only {@code "es"} exists as a second language anywhere in this codebase today (mirrors core's own
 * {@code message_en.yml}/{@code message_es.yml} pair) — hard-coded rather than a generic {@code lang} parameter
 * nothing would ever pass a second value for; parameterize if a third language shows up.
 */
public abstract class LocalizedModuleYaml implements FileInitializer {

	private static final String SPANISH_SUFFIX = "_es";

	private final FileHandler fileHandler;

	protected LocalizedModuleYaml(FileManager fileManager, String baseName) {
		this.fileHandler = resolveFileHandler(fileManager, baseName);
	}

	private static FileHandler resolveFileHandler(FileManager fileManager, String baseName) {
		if ("es".equalsIgnoreCase(Settings.getLanguagePicked())) {
			FileHandler spanish = tryGetFile(fileManager, baseName + SPANISH_SUFFIX);
			if (spanish != null) return spanish;
		}

		FileHandler english = tryGetFile(fileManager, baseName);
		if (english != null) return english;

		throw new PluginException("Neither " + baseName + " nor " + baseName + SPANISH_SUFFIX + " is registered");
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
		return fileHandler.getFileConfiguration().getString(key, fallback);
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

}
