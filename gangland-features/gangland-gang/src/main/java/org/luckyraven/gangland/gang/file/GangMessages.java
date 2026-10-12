package org.luckyraven.gangland.gang.file;

import org.luckyraven.gangland.file.configuration.LocalizedModuleYaml;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * Gang texts from the module's own gang/gang_messages.yml (0.17.0+). Older gang texts still come from message_en.yml.
 * A key missing from the file returns the in-code fallback given at the call site.
 */
public final class GangMessages extends LocalizedModuleYaml {

	public GangMessages(FileManager fileManager) {
		super(fileManager, "gang_messages");
	}

	/**
	 * Raw value of {@code key} (or {@code fallback}), then every %name% of the (name, value, name, value...) pairs
	 * replaced, then coloured. Replacing before colouring keeps the colours of a value such as {@code &cRats}.
	 */
	public String text(String key, String fallback, Object... pairs) {
		String value = raw(key, fallback);
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			value = value.replace("%" + pairs[i] + "%", String.valueOf(pairs[i + 1]));
		}
		return GanglandChatUtil.color(value);
	}

}
