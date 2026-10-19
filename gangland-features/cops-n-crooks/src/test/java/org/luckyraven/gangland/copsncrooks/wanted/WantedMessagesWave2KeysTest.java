package org.luckyraven.gangland.copsncrooks.wanted;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 0.21.0 wave 2 fnd-w2: the getaway and Most Wanted keys exist, and the shipped line equals the in-code fallback. */
@DisplayName("WantedMessages - the 0.21.0 wave 2 keys")
class WantedMessagesWave2KeysTest {

	/** name, path, shipped text (= fallback) */
	private static final String[][] NEW_KEYS = {
			{"GETAWAY_LINE", "Getaway.Line", "&7Getaway: &a+%xp% XP"},
			{"GETAWAY_CAPPED", "Getaway.Capped", "&7Getaway: &8daily XP cap reached"},
			{"GETAWAY_SHARE", "Getaway.Share", "&a%player% got away clean: &f+%xp% XP &7for the crew"},
			{"MOST_WANTED_NEW", "Most_Wanted.New", "&4&lMOST WANTED &f%player% &7is now the city's Most Wanted &c%stars%"},
	};

	private static YamlConfiguration shipped() {
		InputStream in = WantedMessagesWave2KeysTest.class.getClassLoader()
		                                                  .getResourceAsStream("copsncrooks/wanted_messages.yml");
		assertNotNull(in, "copsncrooks/wanted_messages.yml must ship in the module jar");
		return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
	}

	private static WantedMessages.Key key(String name) {
		return Arrays.stream(WantedMessages.Key.values()).filter(k -> k.name().equals(name)).findFirst()
		             .orElseThrow(() -> new AssertionError("WantedMessages.Key." + name + " is missing"));
	}

	@Test
	@DisplayName("every wave 2 key exists with the agreed path and fallback text")
	void keys_existWithPathAndFallback() {
		for (String[] row : NEW_KEYS) {
			WantedMessages.Key key = key(row[0]);
			assertEquals(row[1], key.path, row[0]);
			assertEquals(row[2], key.fallback, row[0]);
		}
	}

	@Test
	@DisplayName("every wave 2 key has its line in the shipped wanted_messages.yml, equal to the in-code fallback")
	void keys_shippedTextEqualsFallback() {
		YamlConfiguration yaml = shipped();

		for (String[] row : NEW_KEYS) {
			assertEquals(row[2], yaml.getString(row[1]), row[1] + " in wanted_messages.yml");
		}
	}
}
