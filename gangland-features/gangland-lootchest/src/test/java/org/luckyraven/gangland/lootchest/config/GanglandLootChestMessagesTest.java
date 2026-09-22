package org.luckyraven.gangland.lootchest.config;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * W53 F1: {@link GanglandLootChestMessages} must pick its backing file by the core {@code Settings.Language}
 * setting the same way {@code LanguageLoader} chooses {@code message_<lang>.yml} — not always read the English
 * file regardless of what the server is configured for. Uses {@code getTimeMessages().getYear()} as the marker
 * read (a {@code Type.NO_CHANGE}-style raw string, no {@link org.luckyraven.gangland.util.GanglandChatUtil}
 * prefix wrapping) so this test doesn't also need {@code Messages.init(...)} wired for {@code ERROR_PREFIX}/etc.
 */
@DisplayName("GanglandLootChestMessages — W53 F1: language-aware file selection")
class GanglandLootChestMessagesTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("Settings.Language 'es' resolves lootchests/lootchest_messages_es.yml")
	void languageEs_resolvesSpanishFile() throws IOException {
		initializeSettingsLanguage("es");
		FileManager fileManager = buildMessagesFileManager(true);

		GanglandLootChestMessages messages = new GanglandLootChestMessages(fileManager);

		assertEquals("marcador-es", messages.getTimeMessages().getYear());
	}

	@Test
	@DisplayName("a non-Spanish Language falls back to the English file")
	void languageOther_fallsBackToEnglish() throws IOException {
		initializeSettingsLanguage("fr");
		FileManager fileManager = buildMessagesFileManager(true);

		GanglandLootChestMessages messages = new GanglandLootChestMessages(fileManager);

		assertEquals("marker-en", messages.getTimeMessages().getYear());
	}

	@Test
	@DisplayName("Language 'es' but the Spanish file isn't registered falls back to English without throwing")
	void languageEs_missingSpanishFile_fallsBackToEnglish() throws IOException {
		initializeSettingsLanguage("es");
		FileManager fileManager = buildMessagesFileManager(false);

		GanglandLootChestMessages messages = new GanglandLootChestMessages(fileManager);

		assertEquals("marker-en", messages.getTimeMessages().getYear());
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

	private FileManager buildMessagesFileManager(boolean includeSpanish) throws IOException {
		Files.writeString(tempDir.resolve("lootchest_messages.yml"), "Time_Units:\n  Year: \"marker-en\"\n",
		                  StandardCharsets.UTF_8);

		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, tempDir.resolve("lootchest_messages.yml").toFile()), false);

		if (includeSpanish) {
			Files.writeString(tempDir.resolve("lootchest_messages_es.yml"), "Time_Units:\n  Year: \"marcador-es\"\n",
			                  StandardCharsets.UTF_8);
			fileManager.addFile(new FileHandler(plugin, tempDir.resolve("lootchest_messages_es.yml").toFile()),
			                    false);
		}

		return fileManager;
	}

}
