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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CivilianMessages - Witness_Calling (the action-bar line a criminal sees)")
class CivilianMessagesWitnessTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("the file's Witness_Calling is returned with colour codes applied")
	void fileValueIsUsed() throws IOException {
		CivilianMessages messages = build("Witness_Calling: \"&cmarker-witness\"\n");

		assertTrue(messages.witnessCalling().contains("marker-witness"));
		assertFalse(messages.witnessCalling().contains("&c"));
	}

	@Test
	@DisplayName("a file without the key falls back to the shipped English line")
	void missingKeyFallsBack() throws IOException {
		CivilianMessages messages = build("List_Empty: \"x\"\n");

		assertTrue(messages.witnessCalling().contains("A witness is calling 911! Stop them!"));
	}

	@Test
	@DisplayName("both shipped message files declare Witness_Calling, the English one with the spec line")
	void shippedFilesDeclareKey() throws IOException {
		String english = resource("npc/civilian_messages.yml");
		String spanish = resource("npc/civilian_messages_es.yml");

		assertTrue(english.contains("\nWitness_Calling: \"&cA witness is calling 911! Stop them!\""));
		assertTrue(spanish.contains("\nWitness_Calling: \""));
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
