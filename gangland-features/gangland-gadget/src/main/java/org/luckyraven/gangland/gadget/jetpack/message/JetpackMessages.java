package org.luckyraven.gangland.gadget.jetpack.message;

import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.IOException;
import java.util.Objects;

/**
 * Jetpack-scoped user-facing strings, backed by the module's own {@code gadget/gadget_messages.yml} (WS7-G4 review
 * I1 — a bare {@code messages} name would collide with any other module's own messages file, since {@link
 * FileManager} resolves purely by name; {@code <module>_messages} is the precedent for every module). New
 * territory for this module (module-API contract: a module's new strings live in its own YAML, never
 * {@code gangland-api}'s {@code Messages} enum) — mirrors {@link
 * org.luckyraven.gangland.gadget.jetpack.config.JetpackAddon}'s constructor/{@link FileInitializer} shape.
 */
public class JetpackMessages implements FileInitializer {

	private final FileHandler fileHandler;

	public JetpackMessages(FileManager fileManager) {
		try {
			String fileName = "gadget_messages";

			fileManager.checkFileLoaded(fileName);

			this.fileHandler = Objects.requireNonNull(fileManager.getFile(fileName));
		} catch (IOException exception) {
			throw new PluginException(exception);
		}
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

	@Override
	public void initialize() {
		// Flat strings read lazily per call — nothing to pre-parse.
	}

	public String noPermission() {
		return GanglandChatUtil.color(get("Jetpack_No_Permission", "&cYou do not have permission to use this "
		                                                           + "jetpack."));
	}

	public String gave(String name, String amount) {
		return GanglandChatUtil.color(get("Jetpack_Gave", "&aGave %amount% jetpack(s) '%name%'.")
				.replace("%name%", name).replace("%amount%", amount));
	}

	public String invalid(String name) {
		return GanglandChatUtil.color(get("Jetpack_Invalid", "&cInvalid jetpack '%name%'.").replace("%name%", name));
	}

	private String get(String key, String fallback) {
		return fileHandler.getFileConfiguration().getString(key, fallback);
	}

}
