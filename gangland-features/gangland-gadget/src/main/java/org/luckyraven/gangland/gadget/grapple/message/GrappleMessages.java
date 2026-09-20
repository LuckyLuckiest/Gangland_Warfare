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

	/**
	 * G4: sent from {@code GrappleLaunchListener} on a refused launch — out of {@code Max_Distance}, outside the
	 * world border, or a blocked line of sight. Not sent for a cooldown/already-active no-op (the player already
	 * knows about those); once per cast attempt, no separate throttle — each attempt is a deliberate player action
	 * ({@code PlayerFishEvent.State#IN_GROUND} fires once per landed hook, not repeatedly), the same rate the
	 * existing permission-denial message already sends at.
	 */
	public String blocked() {
		return GanglandChatUtil.color(get("Grapple_Blocked", "&cThe grapple couldn't reach there."));
	}

	public String gave(String name, String amount) {
		return GanglandChatUtil.color(get("Grapple_Gave", "&aGave %amount% grapple(s) '%name%'.")
				.replace("%name%", name).replace("%amount%", amount));
	}

	public String invalid(String name) {
		return GanglandChatUtil.color(get("Grapple_Invalid", "&cInvalid grapple '%name%'.").replace("%name%", name));
	}

	private String get(String key, String fallback) {
		return fileHandler.getFileConfiguration().getString(key, fallback);
	}

}
