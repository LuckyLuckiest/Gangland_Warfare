package org.luckyraven.gangland.copsncrooks.npc.police.radio;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Lane f2-config: the six 0.20.0 radio lines exist as code defaults (an existing server keeps its old
 * {@code cop_radio_messages*.yml}, docket T-138, and must still hear them), and ship in the English and Spanish files.
 */
@DisplayName("CopRadioMessages - 0.20.0 lines")
class CopRadioMessages020Test {

	private static final Map<String, String> ENGLISH = Map.of(
			"Caller_911", "Dispatch: 911 caller reports %crime% near %place%. Suspect: %suspect%.",
			"Suspect_Description", "Suspect is %suspect%.",
			"Tracker_Tagged", "Tracker's on the car. Dispatch has his position.",
			"Tracker_Ping", "Tracker ping, he's at %place%.",
			"Overwatch_Spotted", "Overwatch, eyes on. He's %distance% out, %direction%.",
			"Patrol_Sighting", "Patrol here, I see the suspect at %place%.");

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("with an empty (old) file every new key still resolves to its English default")
	void oldFile_newKeysHaveCodeDefaults() throws IOException {
		CopRadioMessages messages = build("Lines: {}\n");

		for (Map.Entry<String, String> entry : ENGLISH.entrySet()) {
			assertEquals(List.of(entry.getValue()), messages.lines(entry.getKey()), entry.getKey());
		}
	}

	@Test
	@DisplayName("the shipped English file carries each new key with the default text")
	void shippedEnglish_hasNewKeys() throws IOException {
		YamlConfiguration yaml = shipped("copsncrooks/cop_radio_messages.yml");

		for (Map.Entry<String, String> entry : ENGLISH.entrySet()) {
			assertEquals(List.of(entry.getValue()), yaml.getStringList("Lines." + entry.getKey()), entry.getKey());
		}
	}

	@Test
	@DisplayName("the shipped Spanish file carries each new key, translated, with the same placeholders")
	void shippedSpanish_hasTranslatedNewKeys() throws IOException {
		YamlConfiguration es = shipped("copsncrooks/cop_radio_messages_es.yml");

		for (Map.Entry<String, String> entry : ENGLISH.entrySet()) {
			List<String> lines = es.getStringList("Lines." + entry.getKey());

			assertFalse(lines.isEmpty(), "es " + entry.getKey());
			assertNotEquals(List.of(entry.getValue()), lines, "es " + entry.getKey() + " is not translated");
			for (String placeholder : List.of("%crime%", "%place%", "%suspect%", "%distance%", "%direction%")) {
				assertEquals(entry.getValue().contains(placeholder), String.join(" ", lines).contains(placeholder),
				             entry.getKey() + " " + placeholder);
			}
		}
	}

	private YamlConfiguration shipped(String resource) throws IOException {
		try (InputStream in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resource))) {
			return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}

	private CopRadioMessages build(String yaml) throws IOException {
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		Files.writeString(tempDir.resolve("cop_radio_messages.yml"), yaml, StandardCharsets.UTF_8);
		fileManager.addFile(new FileHandler(plugin, tempDir.resolve("cop_radio_messages.yml").toFile()), false);
		return new CopRadioMessages(fileManager);
	}
}
