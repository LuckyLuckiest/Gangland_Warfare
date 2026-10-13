package org.luckyraven.gangland.npcshops.holdup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.config.ConfigDocument;
import org.luckyraven.keystone.persistence.config.ConfigParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("HoldUpSettings - Hold_Up block of npc/trader_settings.yml")
class HoldUpSettingsTest {

	private static FileHandler handler(String yaml) {
		ConfigDocument document = new ConfigParser().parse(Path.of("trader_settings.yml"), new StringReader(yaml),
		                                                   new ConfigReport());
		FileHandler handler = mock(FileHandler.class);
		when(handler.getParsedDocument()).thenReturn(document);
		when(handler.getFileConfiguration()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
		return handler;
	}

	private static String shippedYaml() throws IOException {
		try (InputStream in = HoldUpSettingsTest.class.getClassLoader().getResourceAsStream(
				"npc/trader_settings.yml")) {
			assertNotNull(in, "npc/trader_settings.yml is shipped in the module jar");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static void assertSpecNumbers(HoldUpSettings s) {
		assertTrue(s.enable());
		assertEquals(6.0, s.range());
		assertEquals(10.0, s.aimAngle());
		assertEquals(10, s.fillSeconds());
		assertEquals(10, s.alarmSeconds());
		assertEquals(0.2, s.dislikeMood());
		assertEquals(4, s.alarmSecondsDisliked());
		assertEquals(200.0, s.takeMin());
		assertEquals(600.0, s.takeMax());
		assertEquals(300, s.cooldownSeconds());
	}

	@Test
	@DisplayName("a file without a Hold_Up block gives the SPEC defaults")
	void missingBlockGivesDefaults() {
		HoldUpSettings s = HoldUpSettings.load(handler("Respawn_Cooldown: 60\n"));
		assertSpecNumbers(s);
		assertFalse(s.filling().isBlank());
		assertFalse(s.done().isBlank());
		assertFalse(s.aborted().isBlank());
		assertFalse(s.closed().isBlank());
		assertEquals(s, HoldUpSettings.defaults());
	}

	@Test
	@DisplayName("values in the Hold_Up block override the defaults key by key")
	void blockValuesOverrideDefaults() {
		String yaml = """
				Hold_Up:
				   Enable: false
				   Range: 8.5
				   Take_Min: 50.0
				   Cooldown_Seconds: 60
				   Messages:
				      Closed: "Shut for now"
				""";
		HoldUpSettings s = HoldUpSettings.load(handler(yaml));
		assertFalse(s.enable());
		assertEquals(8.5, s.range());
		assertEquals(50.0, s.takeMin());
		assertEquals(60, s.cooldownSeconds());
		assertEquals("Shut for now", s.closed());
		assertEquals(10, s.fillSeconds(), "untouched keys keep their default");
		assertEquals(600.0, s.takeMax());
	}

	@Test
	@DisplayName("a non-positive Fill_Seconds or Alarm_Seconds falls back to the default instead of instant payouts")
	void nonPositiveSecondsFallBack() {
		HoldUpSettings s = HoldUpSettings.load(handler("Hold_Up:\n   Fill_Seconds: 0\n   Alarm_Seconds: -3\n"));
		assertEquals(10, s.fillSeconds());
		assertEquals(10, s.alarmSeconds());
	}

	@Test
	@DisplayName("Take_Max below Take_Min is raised to Take_Min")
	void invertedTakeRange() {
		HoldUpSettings s = HoldUpSettings.load(handler("Hold_Up:\n   Take_Min: 500.0\n   Take_Max: 100.0\n"));
		assertEquals(500.0, s.takeMin());
		assertEquals(500.0, s.takeMax());
	}

	@Test
	@DisplayName("the shipped trader_settings.yml documents every Hold_Up key with the SPEC default")
	void shippedFileCarriesTheBlock() throws IOException {
		String yaml = shippedYaml();
		for (String key : List.of("Hold_Up:", "Enable:", "Range:", "Aim_Angle:", "Fill_Seconds:", "Alarm_Seconds:",
		                          "Dislike_Mood:", "Alarm_Seconds_Disliked:", "Take_Min:", "Take_Max:",
		                          "Cooldown_Seconds:", "Messages:", "Filling:", "Done:", "Aborted:", "Closed:")) {
			assertTrue(yaml.contains(key), "trader_settings.yml is missing " + key);
		}
		assertFalse(yaml.contains("{ "), "block-style YAML only");

		HoldUpSettings s = HoldUpSettings.load(handler(yaml));
		assertSpecNumbers(s);
		assertTrue(s.filling().contains("{seconds}"), s.filling());
		assertTrue(s.done().contains("{take}"), s.done());
		assertTrue(s.closed().contains("robbed recently"), s.closed());
	}

	@Test
	@DisplayName("every shipped Hold_Up key line is preceded by a comment line")
	void everyHoldUpKeyHasAComment() throws IOException {
		List<String> lines = shippedYaml().lines().toList();
		int          start = lines.indexOf("Hold_Up:");
		assertTrue(start >= 0, "Hold_Up: block missing");
		for (int i = start + 1; i < lines.size(); i++) {
			String line = lines.get(i);
			if (!line.startsWith(" ")) {
				break; // next top-level key
			}
			String t = line.trim();
			if (t.isEmpty() || t.startsWith("#") || t.equals("Messages:")) {
				continue;
			}
			assertTrue(lines.get(i - 1).trim().startsWith("#"), "no comment above: " + t);
		}
	}

	@Test
	@DisplayName("the shipped file loads to exactly the in-code defaults")
	void shippedFileEqualsDefaults() throws IOException {
		assertEquals(HoldUpSettings.defaults(), HoldUpSettings.load(handler(shippedYaml())));
	}

}
