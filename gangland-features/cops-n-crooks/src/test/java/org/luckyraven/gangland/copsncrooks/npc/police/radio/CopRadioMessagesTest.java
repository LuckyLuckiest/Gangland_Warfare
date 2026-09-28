package org.luckyraven.gangland.copsncrooks.npc.police.radio;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CopRadioMessages — Lines.<key> pool, root special keys, empty means silent, English fallback, es resolves")
class CopRadioMessagesTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("lines(key) reads Lines.<key>; Format/Compass/Sides read at the document root")
	void linesAndRootKeys_parsed() throws IOException {
		CopRadioMessages messages = build("""
				Format: "&9&l[RADIO] &b%unit%&8: &7%line%"
				Compass:
				   - "north"
				Sides:
				   - "front"
				Lines:
				   Contact:
				      - "Contact! %direction%!"
				""");

		assertEquals(List.of("Contact! %direction%!"), messages.lines("Contact"));
		assertEquals(List.of("&9&l[RADIO] &b%unit%&8: &7%line%"), messages.lines("Format"));
		assertEquals(List.of("north"), messages.lines("Compass"));
		assertEquals(List.of("front"), messages.lines("Sides"));
	}

	@Test
	@DisplayName("an explicit empty list is honoured as silent, not replaced by the English fallback")
	void explicitEmptyList_isSilent() throws IOException {
		CopRadioMessages messages = build("""
				Lines:
				   Ack: []
				""");

		assertTrue(messages.lines("Ack").isEmpty());
	}

	@Test
	@DisplayName("a key missing from the file falls back to the hardcoded English default")
	void missingKey_fallsBackToEnglishDefault() throws IOException {
		CopRadioMessages messages = build("Lines: {}\n");

		assertFalse(messages.lines("Responding").isEmpty());
		assertTrue(messages.lines("Responding").get(0).contains("%unit%"));
	}

	@Test
	@DisplayName("an unrecognised key is silent, not an error")
	void unknownKey_isSilent() throws IOException {
		CopRadioMessages messages = build("Lines: {}\n");

		assertTrue(messages.lines("Not_A_Real_Signal").isEmpty());
	}

	@Test
	@DisplayName("Settings.Language 'es' resolves cop_radio_messages_es.yml")
	void languageEs_resolvesSpanishFile() throws IOException {
		initializeSettingsLanguage("es");

		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		writeFile(fileManager, plugin, "cop_radio_messages.yml", "Lines:\n   Contact:\n      - \"en\"\n");
		writeFile(fileManager, plugin, "cop_radio_messages_es.yml", "Lines:\n   Contact:\n      - \"es\"\n");

		CopRadioMessages messages = new CopRadioMessages(fileManager);

		assertEquals(List.of("es"), messages.lines("Contact"));
	}

	private CopRadioMessages build(String yaml) throws IOException {
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		writeFile(fileManager, plugin, "cop_radio_messages.yml", yaml);
		return new CopRadioMessages(fileManager);
	}

	private void writeFile(FileManager fileManager, JavaPlugin plugin, String name, String yaml) throws IOException {
		Files.writeString(tempDir.resolve(name), yaml, StandardCharsets.UTF_8);
		fileManager.addFile(new FileHandler(plugin, tempDir.resolve(name).toFile()), false);
	}

	private void initializeSettingsLanguage(String language) throws IOException {
		Files.writeString(tempDir.resolve("settings.yml"), "Language: " + language + "\nMoney_Symbol: '$'\n",
		                  StandardCharsets.UTF_8);

		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileHandler handler     = new FileHandler(plugin, tempDir.resolve("settings.yml").toFile());
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(handler, false);

		new Settings(fileManager).initialize();
	}
}
