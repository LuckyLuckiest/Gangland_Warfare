package org.luckyraven.gangland.turf.map;

import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.config.TurfMapSettings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.FakeUserLookup;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.luckyraven.keystone.persistence.FileManager;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.List;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-231: a viewer with no gang never reads "attackable" for an unclaimed turf. The map text row, the map hover and the
 * shield/underline styling all follow the gangless rule the HUD already uses (Hud.Gangless). A viewer in a gang still
 * sees the eligibility verdict.
 */
@DisplayName("TurfMapService - gangless viewers on unclaimed turf")
class TurfMapServiceGanglessTest {

	private static final long NOW = 10_000_000_000L;

	private MockedStatic<Settings> settings;
	private FakeGangLookup         gangs;
	private FakeUserLookup         users;
	private TurfManager            turfs;
	private CaptureEligibility     eligibility;
	private TurfMapService         service;
	private World                  world;

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	@BeforeEach
	void setUp() throws Exception {
		settings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		settings.when(Settings::getMoneySymbol).thenReturn("$");
		settings.when(() -> Settings.formatAmount(any())).thenReturn("10");

		gangs = new FakeGangLookup();
		users = new FakeUserLookup();
		turfs = mock(TurfManager.class);
		world = mock(World.class);
		when(world.getName()).thenReturn("world");

		eligibility = mock(CaptureEligibility.class);
		when(eligibility.evaluate(any(), anyLong()))
				.thenReturn(new CaptureEligibility.Verdict(CaptureEligibility.Reason.ATTACKABLE, 0L));

		TurfMessageContract legacy = mock(TurfMessageContract.class);
		when(legacy.formatDuration(anyLong())).thenReturn("5m");
		FileManager fileManager = YamlFixture.fileManager(tempDir, "turf_messages",
		                                                  YamlFixture.shipped("/turf/turf_messages.yml"));
		TurfMessages text = new TurfMessages(fileManager, legacy,
		                                     CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90));

		TurfMapSettings mapSettings = mock(TurfMapSettings.class);
		when(mapSettings.getWidth()).thenReturn(11);
		when(mapSettings.getHeight()).thenReturn(3);
		when(mapSettings.getCellBlocks()).thenReturn(16);

		GarrisonManager garrisons = mock(GarrisonManager.class);
		when(garrisons.count(anyInt())).thenReturn(0);

		service = new TurfMapService(turfs, gangs, users, eligibility, mapSettings, mock(TurfSettings.class),
		                             text, legacy, garrisons);
	}

	@AfterEach
	void tearDown() {
		settings.close();
	}

	private static Turf unclaimed() {
		return new Turf(1, "Docks", new CuboidRegion("world", 0, 0, 15, 15), null, BigDecimal.TEN, 0L, 0L);
	}

	private Player viewerAt(int x, int z) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(new Location(world, x, 64, z));
		return player;
	}

	private Player viewer(UUID uuid) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
		return player;
	}

	private static String plain(String coloured) {
		return ChatColor.stripColor(coloured);
	}

	@Test
	@DisplayName("T-231: the map hover tells a gangless viewer to join a gang, never that the turf is attackable")
	void hover_gangless_isNotAttackable() {
		String hover = plain(service.hoverText(viewer(UUID.randomUUID()), unclaimed(), null, NOW));

		assertTrue(hover.contains("join a gang to capture"), hover);
		assertFalse(hover.contains("attackable"), hover);
	}

	@Test
	@DisplayName("T-231: a viewer in a gang still reads the verdict on an unclaimed turf")
	void hover_inGang_stillSeesAttackable() {
		String hover = plain(service.hoverText(viewer(UUID.randomUUID()), unclaimed(), 3, NOW));

		assertTrue(hover.contains("attackable"), hover);
		assertFalse(hover.contains("join a gang"), hover);
	}

	@Test
	@DisplayName("T-231: the map text row for a gangless viewer carries the gangless sentence, not attackable")
	void textRow_gangless_isNotAttackable() {
		when(turfs.getTurfsInWorld("world")).thenReturn(List.of(unclaimed()));
		Player player = viewer(UUID.randomUUID());
		Player.Spigot spigot = mock(Player.Spigot.class);
		when(player.spigot()).thenReturn(spigot);

		service.sendText(player);

		ArgumentCaptor<BaseComponent[]> sent = ArgumentCaptor.forClass(BaseComponent[].class);
		verify(spigot, atLeastOnce()).sendMessage(sent.capture());
		String row = plain(BaseComponent.toLegacyText(sent.getValue()));
		assertTrue(row.contains("join a gang to capture"), row);
		assertFalse(row.contains("attackable"), row);
	}

	@Test
	@DisplayName("T-231: a gangless viewer's unclaimed turf is never drawn shielded (underlined) from the verdict")
	void raster_gangless_neverUnderlinedFromVerdict() {
		when(eligibility.evaluate(any(), anyLong()))
				.thenReturn(new CaptureEligibility.Verdict(CaptureEligibility.Reason.COOLDOWN, NOW + 60_000L));
		when(turfs.getTurfsInWorld("world")).thenReturn(List.of(unclaimed()));

		TerritoryRaster.Raster raster = service.raster(viewerAt(-40, 0));

		boolean sawTurf = false;
		for (TerritoryRaster.Cell[] row : raster.cells()) {
			for (TerritoryRaster.Cell cell : row) {
				if (cell.turfId() == 1) {
					sawTurf = true;
					assertFalse(cell.style() == TerritoryRaster.Style.UNDERLINE, "gangless cell must not be underlined");
				}
			}
		}
		assertTrue(sawTurf, "the unclaimed turf is in view");
	}
}
