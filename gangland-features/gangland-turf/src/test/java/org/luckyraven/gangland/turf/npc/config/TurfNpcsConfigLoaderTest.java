package org.luckyraven.gangland.turf.npc.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.config.ConfigDocument;
import org.luckyraven.keystone.persistence.config.ConfigParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;

import java.io.StringReader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins that {@link TurfNpcsConfigLoader} re-reads {@code turf/turf_npcs.yml} on a managed reload (TF-36) and does not
 * re-read it on the first lifecycle pass, which the constructor already covered. The handler is a mock whose parsed
 * document is swapped between calls, which is what {@code FileHandler} does after {@code /glw reload}.
 */
@DisplayName("TurfNpcsConfigLoader — turf_npcs.yml reloads on /glw reload (TF-36)")
class TurfNpcsConfigLoaderTest {

	private static final String COP_ENABLED  = "Cop_Response:\n   Enabled: true\n";
	private static final String COP_DISABLED = "Cop_Response:\n   Enabled: false\n";

	@Test
	@DisplayName("a reload picks up an edited Cop_Response.Enabled without a restart")
	void reload_picksUpEditedFile() throws Exception {
		FileHandler handler = mock(FileHandler.class);
		when(handler.getParsedDocument()).thenReturn(document(COP_ENABLED));
		TurfNpcsConfigLoader loader = loader(handler);
		assertEquals(true, loader.getCopGuardConfig().enabled());

		when(handler.getParsedDocument()).thenReturn(document(COP_DISABLED));
		loader.onInitialize(false);

		assertFalse(loader.getCopGuardConfig().enabled());
	}

	@Test
	@DisplayName("the first lifecycle pass does not re-read the file the constructor already loaded")
	void firstLoad_doesNotReloadTwice() throws Exception {
		FileHandler handler = mock(FileHandler.class);
		when(handler.getParsedDocument()).thenReturn(document(COP_ENABLED));
		TurfNpcsConfigLoader loader = loader(handler);
		clearInvocations(handler);

		loader.onInitialize(true);

		verify(handler, never()).getParsedDocument();
	}

	@Test
	@DisplayName("W2-Q: Powerup_Npc.Display_Name is read and defaults to Quartermaster (TF-38)")
	void powerupNpc_displayName_defaultAndOverride() throws Exception {
		FileHandler handler = mock(FileHandler.class);
		when(handler.getParsedDocument()).thenReturn(document("Powerup_Npc:\n   Display_Name: Boss\n"));
		assertEquals("Boss", loader(handler).getPowerupSettings().displayName());

		when(handler.getParsedDocument()).thenReturn(document(COP_ENABLED));
		assertEquals("Quartermaster", loader(handler).getPowerupSettings().displayName());
	}

	@Test
	@DisplayName("W7-B: Powerup_Npc.Placement_Cost is read, defaults to 5000 and is clamped at 0 (D29=B)")
	void placementCost_readAndDefault() throws Exception {
		FileHandler handler = mock(FileHandler.class);
		when(handler.getParsedDocument()).thenReturn(document("Powerup_Npc:\n   Placement_Cost: 2500\n"));
		assertEquals(2500.0, loader(handler).getPowerupSettings().placementCost());

		when(handler.getParsedDocument()).thenReturn(document("Powerup_Npc:\n   Display_Name: Boss\n"));
		assertEquals(5000.0, loader(handler).getPowerupSettings().placementCost());

		when(handler.getParsedDocument()).thenReturn(document("Powerup_Npc:\n   Placement_Cost: -5\n"));
		assertEquals(0.0, loader(handler).getPowerupSettings().placementCost());
	}

	@Test
	@DisplayName("W7-F: Garrison.Hold_Radius is read, defaults to 6 and is clamped to at least 1 block")
	void holdRadius_readDefaultAndClamp() throws Exception {
		FileHandler handler = mock(FileHandler.class);
		when(handler.getParsedDocument()).thenReturn(document("Garrison:\n   Hold_Radius: 9.5\n"));
		assertEquals(9.5, loader(handler).getDefenderConfig().holdRadius());

		when(handler.getParsedDocument()).thenReturn(document(COP_ENABLED));
		assertEquals(6.0, loader(handler).getDefenderConfig().holdRadius());

		when(handler.getParsedDocument()).thenReturn(document("Garrison:\n   Hold_Radius: 0.2\n"));
		assertEquals(1.0, loader(handler).getDefenderConfig().holdRadius());
	}

	@Test
	@DisplayName("W7-E: Owner_Alerts.Cooldown_Seconds is read, defaults to 60, clamps at 0 and reloads")
	void ownerAlertCooldown_readDefaultAndClamp() throws Exception {
		FileHandler handler = mock(FileHandler.class);
		when(handler.getParsedDocument()).thenReturn(document("Owner_Alerts:\n   Cooldown_Seconds: 15\n"));
		TurfNpcsConfigLoader loader = loader(handler);
		assertEquals(15, loader.getOwnerAlertCooldownSeconds());

		when(handler.getParsedDocument()).thenReturn(document("Owner_Alerts:\n   Cooldown_Seconds: 30\n"));
		loader.onInitialize(false);
		assertEquals(30, loader.getOwnerAlertCooldownSeconds());

		when(handler.getParsedDocument()).thenReturn(document(COP_ENABLED));
		assertEquals(60, loader(handler).getOwnerAlertCooldownSeconds());

		when(handler.getParsedDocument()).thenReturn(document("Owner_Alerts:\n   Cooldown_Seconds: -5\n"));
		assertEquals(0, loader(handler).getOwnerAlertCooldownSeconds());
	}

	private static TurfNpcsConfigLoader loader(FileHandler handler) {
		FileManager fileManager = mock(FileManager.class);
		when(fileManager.getFile("turf_npcs")).thenReturn(handler);
		return new TurfNpcsConfigLoader(fileManager);
	}

	private static ConfigDocument document(String yaml) throws Exception {
		return new ConfigParser().parse(Path.of("turf_npcs.yml"), new StringReader(yaml), new ConfigReport());
	}
}
