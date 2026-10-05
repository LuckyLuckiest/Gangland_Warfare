package org.luckyraven.gangland.copsncrooks.detainment.paperwork;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.detainment.DetainedPlayer;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentState;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PaperworkView#fineLine}: the charge-sheet line on the paperwork's info lore, present only for a row that
 * recorded a fine.
 */
@DisplayName("PaperworkView fine line")
class PaperworkViewTest {

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

	private WantedMessages messages() throws IOException {
		Path file = tempDir.resolve("wanted_messages.yml");
		Files.writeString(file, "# fallbacks only\n", StandardCharsets.UTF_8);
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, file.toFile()), false);
		return new WantedMessages(fileManager);
	}

	@Test
	@DisplayName("the line lists the money paid and the extra time")
	void fineLine_listsPaidAndExtraTime() throws IOException {
		DetainedPlayer detained = new DetainedPlayer(UUID.randomUUID(), 1, DetainmentState.JAILED);
		detained.setFinePaid(300.0);
		detained.setFineExtraSeconds(40);

		String line = PaperworkView.fineLine(messages(), detained);

		assertNotNull(line);
		String plain = ChatColor.stripColor(line);
		assertTrue(plain.contains("Fine paid: " + Settings.getMoneySymbol() +
		                          Settings.formatAmount(BigDecimal.valueOf(300.0))), plain);
		assertTrue(plain.contains("Extra time: 40s"), plain);
	}

	@Test
	@DisplayName("a row from before the charge sheet has no line")
	void fineLine_legacyRow_isNull() throws IOException {
		assertNull(PaperworkView.fineLine(messages(), new DetainedPlayer(UUID.randomUUID(), 1,
		                                                                 DetainmentState.JAILED)));
	}
}
