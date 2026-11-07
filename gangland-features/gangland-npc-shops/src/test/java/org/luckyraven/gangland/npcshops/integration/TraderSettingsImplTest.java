package org.luckyraven.gangland.npcshops.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.config.ConfigParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Characterises the file-backed getters of {@link TraderSettingsImpl} before they become Lombok {@code @Getter}s. */
@DisplayName("TraderSettingsImpl - file-backed getters")
class TraderSettingsImplTest {

	private static FileManager managerFor(String yaml) throws Exception {
		FileHandler handler = mock(FileHandler.class);
		when(handler.getParsedDocument()).thenReturn(
				new ConfigParser().parse(Path.of("trader_settings.yml"), new StringReader(yaml), new ConfigReport()));
		FileManager fileManager = mock(FileManager.class);
		when(fileManager.getFile("trader_settings")).thenReturn(handler);
		return fileManager;
	}

	@Test
	@DisplayName("every getter returns the value read from the file")
	void getters_returnConfiguredValues() throws Exception {
		String yaml = "Respawn_Cooldown: 90\n" + "Head_Track_Radius: 12\n" + "Fallback_Trait_Id: grumpy\n"
		              + "Tip_Amount: 250.5\n" + "Sell:\n   Max_Offer_Slots: 7\n   Mood_Per_Sale: 0.5\n";

		TraderSettingsImpl settings = new TraderSettingsImpl(managerFor(yaml));

		assertEquals(90, settings.getRespawnCooldownSeconds());
		assertEquals(12, settings.getHeadTrackRadius());
		assertEquals("grumpy", settings.getFallbackTraitId());
		assertEquals(7, settings.getSellMaxOfferSlots());
		assertEquals(0.5, settings.getMoodPerSale());
		assertEquals(new BigDecimal("250.5"), settings.getTipAmount());
	}

	@Test
	@DisplayName("every getter returns its default when the file is empty")
	void getters_returnDefaultsForEmptyFile() throws Exception {
		TraderSettingsImpl settings = new TraderSettingsImpl(managerFor("Unrelated: 1\n"));

		assertEquals(60, settings.getRespawnCooldownSeconds());
		assertEquals(8, settings.getHeadTrackRadius());
		assertEquals("easygoing", settings.getFallbackTraitId());
		assertEquals(20, settings.getSellMaxOfferSlots());
		assertEquals(0.02, settings.getMoodPerSale());
		assertEquals(BigDecimal.valueOf(100), settings.getTipAmount());
	}

	@Test
	@DisplayName("an unparsable Tip_Amount falls back to 100")
	void tipAmount_fallsBackWhenNotANumber() throws Exception {
		TraderSettingsImpl settings = new TraderSettingsImpl(managerFor("Tip_Amount: lots\n"));

		assertEquals(BigDecimal.valueOf(100), settings.getTipAmount());
	}

	@Test
	@DisplayName("onInitialize re-reads the file")
	void onInitialize_rereadsTheFile() throws Exception {
		FileHandler handler = mock(FileHandler.class);
		when(handler.getParsedDocument()).thenReturn(
				new ConfigParser().parse(Path.of("trader_settings.yml"), new StringReader("Head_Track_Radius: 3\n"),
				                         new ConfigReport()));
		FileManager fileManager = mock(FileManager.class);
		when(fileManager.getFile("trader_settings")).thenReturn(handler);
		TraderSettingsImpl settings = new TraderSettingsImpl(fileManager);
		assertEquals(3, settings.getHeadTrackRadius());

		when(handler.getParsedDocument()).thenReturn(
				new ConfigParser().parse(Path.of("trader_settings.yml"), new StringReader("Head_Track_Radius: 5\n"),
				                         new ConfigReport()));
		settings.onInitialize(false);

		assertEquals(5, settings.getHeadTrackRadius());
	}
}
