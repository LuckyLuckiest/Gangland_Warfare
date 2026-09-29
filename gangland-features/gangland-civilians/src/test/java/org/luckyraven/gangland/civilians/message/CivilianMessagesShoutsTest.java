package org.luckyraven.gangland.civilians.message;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CivilianMessages RadioLines — Shouts.Lines.<key> pool, empty means silent, English fallback, es resolves")
class CivilianMessagesShoutsTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("lines(key) reads Shouts.Lines.<key>; Format/Compass/Sides read at Shouts' own root")
	void linesAndRootKeys_parsed() throws IOException {
		CivilianMessages messages = build("""
				Shouts:
				   Format: "&c[%faction%] &f%unit%&7: &f%line%"
				   Compass:
				      - "north"
				   Sides:
				      - "front"
				   Lines:
				      Contact:
				         - "There he is!"
				""");

		assertEquals(List.of("There he is!"), messages.lines("Contact"));
		assertEquals(List.of("&c[%faction%] &f%unit%&7: &f%line%"), messages.lines("Format"));
		assertEquals(List.of("north"), messages.lines("Compass"));
		assertEquals(List.of("front"), messages.lines("Sides"));
	}

	@Test
	@DisplayName("an explicit empty list is honoured as silent, not replaced by the English fallback")
	void explicitEmptyList_isSilent() throws IOException {
		CivilianMessages messages = build("""
				Shouts:
				   Lines:
				      Ack: []
				""");

		assertTrue(messages.lines("Ack").isEmpty());
	}

	@Test
	@DisplayName("a key missing from the file falls back to the hardcoded English default; Rally is present")
	void missingKey_fallsBackToEnglishDefault() throws IOException {
		CivilianMessages messages = build("Shouts:\n   Lines: {}\n");

		assertFalse(messages.lines("Rally").isEmpty());
		assertTrue(messages.lines("Rally").get(0).contains("%faction%"));
	}

	@Test
	@DisplayName("an unrecognised key is silent, not an error")
	void unknownKey_isSilent() throws IOException {
		CivilianMessages messages = build("Shouts:\n   Lines: {}\n");

		assertTrue(messages.lines("Not_A_Real_Signal").isEmpty());
	}

	@Test
	@DisplayName("Settings.Language 'es' resolves civilian_messages_es.yml's Shouts block")
	void languageEs_resolvesSpanishFile() throws IOException {
		initializeSettingsLanguage("es");

		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		writeFile(fileManager, plugin, "civilian_messages.yml", "Shouts:\n   Lines:\n      Contact:\n         - \"en\"\n");
		writeFile(fileManager, plugin, "civilian_messages_es.yml",
		         "Shouts:\n   Lines:\n      Contact:\n         - \"es\"\n");

		CivilianMessages messages = new CivilianMessages(fileManager);

		assertEquals(List.of("es"), messages.lines("Contact"));
	}

	@Test
	@DisplayName("Fall_Back and In_Cover (retreat to cover) have English defaults and lines in both shipped files")
	void retreatLines_presentInDefaultsAndShippedFiles() throws IOException {
		CivilianMessages fallback = build("Shouts:\n   Lines: {}\n");
		assertFalse(fallback.lines("Fall_Back").isEmpty());
		assertFalse(fallback.lines("In_Cover").isEmpty());

		CivilianMessages english = build(shipped("npc/civilian_messages.yml"));
		assertFalse(english.lines("Fall_Back").isEmpty());
		assertFalse(english.lines("In_Cover").isEmpty());

		initializeSettingsLanguage("es");
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		writeFile(fileManager, plugin, "civilian_messages.yml", shipped("npc/civilian_messages.yml"));
		writeFile(fileManager, plugin, "civilian_messages_es.yml", shipped("npc/civilian_messages_es.yml"));
		CivilianMessages spanish = new CivilianMessages(fileManager);

		assertFalse(spanish.lines("Fall_Back").isEmpty());
		assertFalse(spanish.lines("In_Cover").isEmpty());
		assertNotEquals(english.lines("Fall_Back"), spanish.lines("Fall_Back"));
		assertNotEquals(english.lines("In_Cover"), spanish.lines("In_Cover"));
	}

	private String shipped(String resource) throws IOException {
		try (InputStream in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resource))) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private CivilianMessages build(String yaml) throws IOException {
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		writeFile(fileManager, plugin, "civilian_messages.yml", yaml);
		return new CivilianMessages(fileManager);
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
