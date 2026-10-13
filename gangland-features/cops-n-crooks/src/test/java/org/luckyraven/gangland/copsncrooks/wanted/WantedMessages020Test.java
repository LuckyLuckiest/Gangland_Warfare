package org.luckyraven.gangland.copsncrooks.wanted;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lane f2-config: the player-facing 0.20.0 lines (patrol warnings, tickets, tracker, trespass, suspect description
 * words) have a non-blank in-code fallback equal to the shipped {@code wanted_messages.yml} text, with the placeholders
 * the feature lanes fill.
 */
@DisplayName("WantedMessages - 0.20.0 keys")
class WantedMessages020Test {

	/** Key -> placeholders its text must carry. */
	private static final Map<WantedMessages.Key, List<String>> PLACEHOLDERS = Map.ofEntries(
			Map.entry(WantedMessages.Key.PATROL_PUT_AWAY, List.of("%seconds%")),
			Map.entry(WantedMessages.Key.PATROL_COMPLIED, List.of()),
			Map.entry(WantedMessages.Key.PATROL_PULL_OVER, List.of("%seconds%")),
			Map.entry(WantedMessages.Key.PATROL_TICKET, List.of("%money_symbol%", "%amount%")),
			Map.entry(WantedMessages.Key.PATROL_FLED, List.of()),
			Map.entry(WantedMessages.Key.TRACKER_TAGGED, List.of()),
			Map.entry(WantedMessages.Key.TRACKER_HUD, List.of("%seconds%")),
			Map.entry(WantedMessages.Key.TRESPASS, List.of()),
			Map.entry(WantedMessages.Key.SUSPECT_UNKNOWN, List.of()),
			Map.entry(WantedMessages.Key.SUSPECT_BARE, List.of()),
			Map.entry(WantedMessages.Key.SUSPECT_ON_FOOT, List.of()),
			Map.entry(WantedMessages.Key.SUSPECT_IN_CAR, List.of()),
			Map.entry(WantedMessages.Key.SUSPECT_FORMAT, List.of("%who%", "%mask%", "%chest%", "%vehicle%")));

	@TempDir
	Path tempDir;

	@BeforeAll
	static void primeMoneySymbol() throws ReflectiveOperationException {
		Field field = Settings.class.getDeclaredField("moneySymbol");
		field.setAccessible(true);
		if (field.get(null) == null) {
			field.set(null, "$");
		}
	}

	@Test
	@DisplayName("every new key has a non-blank fallback that carries its placeholders")
	void newKeys_haveFallbacksWithPlaceholders() {
		for (Map.Entry<WantedMessages.Key, List<String>> entry : PLACEHOLDERS.entrySet()) {
			String fallback = entry.getKey().fallback;

			assertFalse(fallback.isBlank(), entry.getKey() + " has a blank fallback");
			for (String placeholder : entry.getValue()) {
				assertTrue(fallback.contains(placeholder), entry.getKey() + " lacks " + placeholder);
			}
		}
	}

	@Test
	@DisplayName("the spec's wording: put away, pull over, tagged car, restricted area, and the suspect words")
	void newKeys_specWording() {
		assertTrue(WantedMessages.Key.PATROL_PUT_AWAY.fallback.contains("Put that away"));
		assertTrue(WantedMessages.Key.PATROL_PULL_OVER.fallback.contains("Pull over"));
		assertTrue(WantedMessages.Key.TRACKER_TAGGED.fallback.contains("Your car has been tagged. Ditch it."));
		assertTrue(WantedMessages.Key.TRESPASS.fallback.contains("Restricted area"));
		assertEquals("unknown suspect", WantedMessages.Key.SUSPECT_UNKNOWN.fallback);
		assertEquals("suspect", WantedMessages.Key.SUSPECT_BARE.fallback);
		assertEquals("on foot", WantedMessages.Key.SUSPECT_ON_FOOT.fallback);
		assertEquals("in a car", WantedMessages.Key.SUSPECT_IN_CAR.fallback);
		assertEquals("%who%%mask%, %chest%, %vehicle%", WantedMessages.Key.SUSPECT_FORMAT.fallback);
	}

	@Test
	@DisplayName("the shipped wanted_messages.yml carries each new key with exactly the fallback text")
	void shippedFile_matchesFallbacks() throws IOException {
		YamlConfiguration yaml = shipped();

		for (WantedMessages.Key key : PLACEHOLDERS.keySet()) {
			assertTrue(yaml.isString(key.path), key.path + " is missing from wanted_messages.yml");
			assertEquals(key.fallback, yaml.getString(key.path), key.path);
		}
	}

	@Test
	@DisplayName("an old file without the keys still reads: format fills the placeholders of the fallback")
	void oldFile_readsFallback() throws IOException {
		WantedMessages messages = messages("Hud:\n   Bar:\n      Seen: \"&c%stars%\"\n");

		String ticket = messages.format(WantedMessages.Key.PATROL_TICKET, Map.of("amount", "150"));

		assertFalse(ticket.isBlank());
		assertTrue(ticket.contains("150"), ticket);
		assertFalse(ticket.contains("%amount%"), ticket);
	}

	private YamlConfiguration shipped() throws IOException {
		try (InputStream in = Objects.requireNonNull(
				getClass().getClassLoader().getResourceAsStream("copsncrooks/wanted_messages.yml"))) {
			return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}

	private WantedMessages messages(String yaml) throws IOException {
		Files.writeString(tempDir.resolve("wanted_messages.yml"), yaml, StandardCharsets.UTF_8);
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, tempDir.resolve("wanted_messages.yml").toFile()), false);
		return new WantedMessages(fileManager);
	}
}
