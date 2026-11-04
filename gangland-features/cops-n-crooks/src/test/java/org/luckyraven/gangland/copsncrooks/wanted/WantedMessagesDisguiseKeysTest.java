package org.luckyraven.gangland.copsncrooks.wanted;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 0.21.5 disguise wave: the suspect-description parts have a path, a fallback with their slot and the shipped text. */
@DisplayName("WantedMessages - 0.21.5 suspect description keys")
class WantedMessagesDisguiseKeysTest {

	/** Key -> the slot its text must carry. */
	private static final Map<WantedMessages.Key, List<String>> SUSPECT_KEYS = Map.of(
			WantedMessages.Key.SUSPECT_MASKED, List.of("%head%"),
			WantedMessages.Key.SUSPECT_WEARING, List.of("%items%"),
			WantedMessages.Key.SUSPECT_PLAIN, List.of(),
			WantedMessages.Key.SUSPECT_ARMED, List.of("%weapon%"));

	@Test
	@DisplayName("the four new Suspect keys have their path, a fallback with their slot, and the shipped file text")
	void suspectKeysMatchTheShippedFile() throws IOException {
		YamlConfiguration yaml = shipped();

		for (Map.Entry<WantedMessages.Key, List<String>> entry : SUSPECT_KEYS.entrySet()) {
			WantedMessages.Key key = entry.getKey();

			assertTrue(key.path.startsWith("Suspect."), key.path);
			assertFalse(key.fallback.isBlank(), key + " has a blank fallback");
			for (String slot : entry.getValue()) {
				assertTrue(key.fallback.contains(slot), key + " lacks " + slot);
			}
			assertTrue(yaml.isString(key.path), key.path + " is missing from wanted_messages.yml");
			assertEquals(key.fallback, yaml.getString(key.path), key.path);
		}
	}

	@Test
	@DisplayName("850: the three Disguise keys have their path, a fallback, and the shipped file text; Recognised carries %suspect%")
	void disguiseKeysMatchTheShippedFile() throws IOException {
		YamlConfiguration yaml = shipped();
		List<WantedMessages.Key> keys = List.of(WantedMessages.Key.DISGUISE_RECOGNISED,
		                                        WantedMessages.Key.DISGUISE_UNRECOGNISED,
		                                        WantedMessages.Key.DISGUISE_OUTFIT);

		for (WantedMessages.Key key : keys) {
			assertTrue(key.path.startsWith("Disguise."), key.path);
			assertFalse(key.fallback.isBlank(), key + " has a blank fallback");
			assertTrue(yaml.isString(key.path), key.path + " is missing from wanted_messages.yml");
			assertEquals(key.fallback, yaml.getString(key.path), key.path);
		}
		assertTrue(WantedMessages.Key.DISGUISE_RECOGNISED.fallback.contains("%suspect%"));
		assertTrue(yaml.getString("Disguise.Recognised").contains("%suspect%"));
	}

	@Test
	@DisplayName("0.21.5: the Spanish file carries every new key, Suspect.Unknown, On_Foot and In_Car, keeps each placeholder, and adds no key the English file lacks")
	void spanishFileCarriesEveryNewKey() throws IOException {
		YamlConfiguration english = shipped();
		YamlConfiguration spanish = load("copsncrooks/wanted_messages_es.yml");
		List<WantedMessages.Key> keys = List.of(WantedMessages.Key.SUSPECT_MASKED, WantedMessages.Key.SUSPECT_WEARING,
		                                        WantedMessages.Key.SUSPECT_PLAIN, WantedMessages.Key.SUSPECT_ARMED,
		                                        WantedMessages.Key.DISGUISE_RECOGNISED,
		                                        WantedMessages.Key.DISGUISE_UNRECOGNISED,
		                                        WantedMessages.Key.DISGUISE_OUTFIT, WantedMessages.Key.SUSPECT_UNKNOWN,
		                                        WantedMessages.Key.SUSPECT_ON_FOOT, WantedMessages.Key.SUSPECT_IN_CAR);

		for (WantedMessages.Key key : keys) {
			assertTrue(spanish.isString(key.path), key.path + " is missing from wanted_messages_es.yml");
			Matcher slot = Pattern.compile("%\\w+%").matcher(key.fallback);
			while (slot.find()) {
				assertTrue(spanish.getString(key.path).contains(slot.group()), key.path + " lacks " + slot.group());
			}
		}
		for (String path : spanish.getKeys(true)) {
			if (spanish.isString(path)) {
				assertTrue(english.isString(path), path + " is in wanted_messages_es.yml but not in the English file");
			}
		}
	}

	private YamlConfiguration shipped() throws IOException {
		return load("copsncrooks/wanted_messages.yml");
	}

	private YamlConfiguration load(String resource) throws IOException {
		try (InputStream in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resource),
		                                              resource)) {
			return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}
}
