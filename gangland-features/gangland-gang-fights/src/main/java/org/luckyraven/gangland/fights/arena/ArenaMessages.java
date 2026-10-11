package org.luckyraven.gangland.fights.arena;

import org.luckyraven.gangland.file.configuration.LocalizedModuleYaml;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.persistence.FileManager;

/** The {@code Arenas} texts of {@code fight/fight_messages.yml}. */
public final class ArenaMessages extends LocalizedModuleYaml {

	public ArenaMessages(FileManager fileManager) {
		super(fileManager, "fight_messages");
	}

	/** The text under {@code key} (a full {@code Arenas.*} key) with every %name% of the (name, value...) pairs replaced. */
	public String text(String key, Object... pairs) {
		String value = raw(key, key);
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			value = value.replace("%" + pairs[i] + "%", String.valueOf(pairs[i + 1]));
		}
		return GanglandChatUtil.color(value);
	}
}
