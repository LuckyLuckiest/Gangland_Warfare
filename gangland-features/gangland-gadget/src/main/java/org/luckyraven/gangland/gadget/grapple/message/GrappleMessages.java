package org.luckyraven.gangland.gadget.grapple.message;

import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.IOException;
import java.util.Objects;

/**
 * Grapple-scoped user-facing strings, backed by the module's shared {@code gadget/gadget_messages.yml} — same file
 * {@link org.luckyraven.gangland.gadget.jetpack.message.JetpackMessages} already registers (the {@code
 * grappleMessages} bean takes a {@code JetpackMessages} parameter purely to force bean-graph ordering after that
 * registration), so this constructor only looks the file up, it never calls {@code fileManager.addFile(...)} —
 * mirrors {@link org.luckyraven.gangland.gadget.jetpack.message.JetpackMessages} exactly.
 */
public class GrappleMessages implements FileInitializer {

	private final FileHandler fileHandler;

	public GrappleMessages(FileManager fileManager) {
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
		return GanglandChatUtil.color(get("Grapple_No_Permission", "&cYou do not have permission to use this "
		                                                           + "grapple."));
	}

	private String get(String key, String fallback) {
		return fileHandler.getFileConfiguration().getString(key, fallback);
	}

}
