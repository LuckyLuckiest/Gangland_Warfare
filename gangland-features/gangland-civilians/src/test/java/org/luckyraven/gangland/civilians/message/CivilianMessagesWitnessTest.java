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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CivilianMessages - Witness_Calling (the action-bar line a criminal sees)")
class CivilianMessagesWitnessTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("the file's Witness_Calling is returned with colour codes applied and %caller%/%seconds% filled")
	void fileValueIsUsed() throws IOException {
		CivilianMessages messages = build("Witness_Calling: \"&cmarker %caller% %seconds%s\"\n");

		assertEquals("\u00a7cmarker Pedestrian 5s", messages.witnessCalling("Pedestrian", 5));
	}

	@Test
	@DisplayName("an old placeholder-free Witness_Calling still renders as written")
	void oldTextStillRenders() throws IOException {
		CivilianMessages messages = build("Witness_Calling: \"&cA witness is calling 911! Stop them!\"\n");

		assertTrue(messages.witnessCalling("Pedestrian", 5).contains("A witness is calling 911! Stop them!"));
	}

	@Test
	@DisplayName("a file without the keys falls back to the shipped English countdown, stopped line and place word")
	void missingKeyFallsBack() throws IOException {
		CivilianMessages messages = build("List_Empty: \"x\"\n");

		assertEquals("\u00a7c911 \u00a7fPedestrian \u00a77is calling - \u00a7e5s", messages.witnessCalling("Pedestrian", 5));
		assertEquals("\u00a7aCall stopped.", messages.witnessStopped());
		assertEquals("here", messages.witnessUnknownPlace());
	}

	@Test
	@DisplayName("Witness_Format and the three Witness pools have shipped defaults, one line each")
	void witnessShoutDefaults() throws IOException {
		CivilianMessages messages = build("List_Empty: \"x\"\n");

		assertEquals(List.of("&c[911] &f%unit%&7: &f%line%"), messages.lines("Witness_Format"));
		assertEquals(List.of("Hey! I saw that! I'm calling the cops!"), messages.lines("Witness_Start"));
		assertEquals(List.of("Okay, okay! I'm hanging up!"), messages.lines("Witness_Scared"));
		assertEquals(List.of("Yes, police? He's right here, near %place%!"), messages.lines("Witness_Landed"));
	}

	@Test
	@DisplayName("Witness_Format is read at the Shouts root, the pools under Shouts.Lines")
	void witnessShoutKeysRead() throws IOException {
		CivilianMessages messages = build("""
				Shouts:
				   Witness_Format: "F %line%"
				   Lines:
				      Witness_Start:
				         - "S"
				""");

		assertEquals(List.of("F %line%"), messages.lines("Witness_Format"));
		assertEquals(List.of("S"), messages.lines("Witness_Start"));
	}

	@Test
	@DisplayName("both shipped message files declare every 0.21.1 Witness key; English with the spec countdown line")
	void shippedFilesDeclareKey() throws IOException {
		String english = resource("npc/civilian_messages.yml").replace("\r\n", "\n");
		String spanish = resource("npc/civilian_messages_es.yml").replace("\r\n", "\n");

		assertTrue(english.contains("\nWitness_Calling: \"&c911 &f%caller% &7is calling - &e%seconds%s\""));
		for (String file : List.of(english, spanish)) {
			for (String key : List.of("\nWitness_Calling: \"", "\nWitness_Stopped: \"", "\nWitness_Unknown_Place: \"",
			                          "\n   Witness_Format: \"", "\n      Witness_Start:\n         - \"",
			                          "\n      Witness_Scared:\n         - \"", "\n      Witness_Landed:\n         - \"")) {
				assertTrue(file.contains(key), key);
			}
		}
	}

	private String resource(String name) throws IOException {
		try (InputStream in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(name))) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private CivilianMessages build(String yaml) throws IOException {
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
		Files.writeString(tempDir.resolve("settings.yml"), "Language: en\nMoney_Symbol: '$'\n", StandardCharsets.UTF_8);
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, tempDir.resolve("settings.yml").toFile()), false);
		new Settings(fileManager).initialize();

		Files.writeString(tempDir.resolve("civilian_messages.yml"), yaml, StandardCharsets.UTF_8);
		fileManager.addFile(new FileHandler(plugin, tempDir.resolve("civilian_messages.yml").toFile()), false);
		return new CivilianMessages(fileManager);
	}
}
