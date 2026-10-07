package org.luckyraven.gangland.copsncrooks.wanted;

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
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link WantedMessages}: placeholder and colour substitution, the in-code fallback for a missing key, the crime-name
 * lookup and the duration formatter.
 */
@DisplayName("WantedMessages")
class WantedMessagesTest {

	@TempDir
	Path tempDir;

	/** Colouring a line reads {@code Settings.moneySymbol}, which only a loaded settings.yml sets. */
	@BeforeAll
	static void primeMoneySymbol() throws ReflectiveOperationException {
		Field field = Settings.class.getDeclaredField("moneySymbol");
		field.setAccessible(true);
		if (field.get(null) == null) {
			field.set(null, "$");
		}
	}

	private WantedMessages messages(String yaml) throws IOException {
		Files.writeString(tempDir.resolve("wanted_messages.yml"), yaml, StandardCharsets.UTF_8);
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, tempDir.resolve("wanted_messages.yml").toFile()), false);
		return new WantedMessages(fileManager);
	}

	@Test
	@DisplayName("format replaces every %name% and the colour codes, and falls back to the in-code text when the key is missing")
	void format_replacesPlaceholdersAndColours_fallsBackWhenMissing() throws IOException {
		WantedMessages messages = messages("Hud:\n   Bar:\n      Seen: \"&c%stars% seen by %who%\"\n");

		assertEquals("§c*** seen by Rex",
		             messages.format(WantedMessages.Key.BAR_SEEN, Map.of("stars", "***", "who", "Rex")));
		// Hud.Title is absent from the file: the fallback "&c%stars%" is used.
		assertEquals("§c**", messages.format(WantedMessages.Key.TITLE, Map.of("stars", "**")));
	}

	@Test
	@DisplayName("the shipped file's Unknown_Crime line is the one crimeName(\"Unknown_Crime\") reads")
	void shippedUnknownCrime_isReadable() throws IOException {
		String shipped = Files.readString(Path.of("src/main/resources/copsncrooks/wanted_messages.yml"), StandardCharsets.UTF_8);

		assertEquals("Reported crime", messages(shipped).crimeName("Unknown_Crime"));
	}

	@Test
	@DisplayName("crimeName reads Crimes.<id>, else the id with spaces")
	void crimeName_fallsBackToTheSpacedId() throws IOException {
		WantedMessages messages = messages("Crimes:\n   Kill_Cop: \"Killing an officer\"\n");

		assertEquals("Killing an officer", messages.crimeName("Kill_Cop"));
		assertEquals("Safe Cracking", messages.crimeName("Safe_Cracking"));
	}

	@Test
	@DisplayName("the bribe-star lines fall back in code on a file that predates them")
	void bribeStarKeys_fallBackOnAnOldFile() throws IOException {
		WantedMessages messages = messages("Hud:\n   Title: \"&c%stars%\"\n");

		assertEquals("§6You pocketed a police bribe star. §e-2 star(s).",
		             messages.format(WantedMessages.Key.BRIBE_STAR_TAKEN, Map.of("stars", "2")));
		assertEquals("§cNot with a cop watching.", messages.format(WantedMessages.Key.BRIBE_STAR_SEEN, Map.of()));
	}

	@Test
	@DisplayName("duration is whole seconds under a minute, else minutes and zero-padded seconds")
	void duration_formats() {
		assertEquals("45s", WantedMessages.duration(45));
		assertEquals("1m 05s", WantedMessages.duration(65));
	}
}
