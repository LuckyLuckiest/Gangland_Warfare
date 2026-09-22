package org.luckyraven.gangland.civilians.message;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WS6 G3: {@link CivilianMessages} (built on the new shared {@link org.luckyraven.gangland.file.configuration
 * .LocalizedModuleYaml}) must pick its backing file by the core {@code Settings.Language} setting the same way
 * {@code GanglandLootChestMessages} does (W53 F1) — not always read the English file regardless of what the
 * server is configured for. Uses {@code listEmpty()} as the marker read; unlike the loot-chest precedent's raw,
 * unwrapped marker, every {@code CivilianMessages} accessor is {@code Type.COMMAND}/{@code Type.PREFIX}-shaped
 * (matches the deleted {@code Messages.CIVILIAN_*} constants exactly), so {@link Messages#init} needs a stub
 * {@link MessageProvider} first — otherwise {@code GanglandChatUtil.commandMessage} NPEs reading
 * {@code Messages.COMMAND_PREFIX} off an uninitialized provider.
 */
@DisplayName("CivilianMessages — language-aware file selection (LocalizedModuleYaml)")
class CivilianMessagesTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("Settings.Language 'es' resolves npc/civilian_messages_es.yml")
	void languageEs_resolvesSpanishFile() throws IOException {
		initMessagesProvider();
		initializeSettingsLanguage("es");
		FileManager fileManager = buildMessagesFileManager(true);

		CivilianMessages messages = new CivilianMessages(fileManager);

		assertEquals("marcador-es", messages.listEmpty());
	}

	@Test
	@DisplayName("a non-Spanish Language falls back to the English file")
	void languageOther_fallsBackToEnglish() throws IOException {
		initMessagesProvider();
		initializeSettingsLanguage("fr");
		FileManager fileManager = buildMessagesFileManager(true);

		CivilianMessages messages = new CivilianMessages(fileManager);

		assertEquals("marker-en", messages.listEmpty());
	}

	@Test
	@DisplayName("Language 'es' but the Spanish file isn't registered falls back to English without throwing")
	void languageEs_missingSpanishFile_fallsBackToEnglish() throws IOException {
		initMessagesProvider();
		initializeSettingsLanguage("es");
		FileManager fileManager = buildMessagesFileManager(false);

		CivilianMessages messages = new CivilianMessages(fileManager);

		assertEquals("marker-en", messages.listEmpty());
	}

	/** Stubs {@link Messages#init} so {@code Commands.Prefix} resolves to "" instead of NPE-ing on a null provider. */
	private static void initMessagesProvider() {
		Messages.init(new MessageProvider() {
			@Override
			public String getString(String path) {
				return "";
			}

			@Override
			public List<String> getStringList(String path) {
				return List.of();
			}
		});
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
		Files.writeString(tempDir.resolve("civilian_messages.yml"), "List_Empty: \"marker-en\"\n",
		                  StandardCharsets.UTF_8);

		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, tempDir.resolve("civilian_messages.yml").toFile()), false);

		if (includeSpanish) {
			Files.writeString(tempDir.resolve("civilian_messages_es.yml"), "List_Empty: \"marcador-es\"\n",
			                  StandardCharsets.UTF_8);
			fileManager.addFile(new FileHandler(plugin, tempDir.resolve("civilian_messages_es.yml").toFile()),
			                    false);
		}

		return fileManager;
	}

}
