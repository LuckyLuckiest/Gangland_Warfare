package org.luckyraven.gangland.fights.fight;

import org.luckyraven.gangland.file.configuration.LocalizedModuleYaml;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.persistence.FileManager;

/** Texts of {@code fight/fight_messages.yml}. A key missing from the file shows as the key itself, in red. */
public final class FightMessages extends LocalizedModuleYaml {

	public FightMessages(FileManager fileManager) {
		super(fileManager, "fight_messages");
	}

	/** The value of {@code key} with every %name% of the (name, value, ...) pairs replaced, then coloured. */
	public String text(String key, Object... pairs) {
		String value = raw(key, "&c" + key);
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			value = value.replace("%" + pairs[i] + "%", String.valueOf(pairs[i + 1]));
		}
		return GanglandChatUtil.color(value);
	}
}
