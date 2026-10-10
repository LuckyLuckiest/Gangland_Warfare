package org.luckyraven.gangland.turf.map;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.capture.TurfRelation;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.config.TurfMapSettings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.gangland.turf.state.TurfState;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.FakeUserLookup;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.luckyraven.keystone.persistence.FileManager;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@link TurfMapRenderer}: run-length merge, click and hover of turf runs, no bold anywhere; and, through
 * {@link TurfMapService} with mocks, the owners-only and own-gang-only facts never reach the wrong viewer.
 */
@DisplayName("TurfMapRenderer and the hover facts - components and who sees what")
class TurfMapRendererTest {

	private static final long NOW = 10_000_000_000L;

	private MockedStatic<Settings> settings;

	private FakeGangLookup gangs;
	private FakeUserLookup users;
	private GarrisonManager garrisons;
	private TurfManager turfManager;
	private TurfMapService service;

	@BeforeEach
	void setUp() {
		settings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		settings.when(Settings::getMoneySymbol).thenReturn("$");
		settings.when(() -> Settings.formatAmount(any())).thenReturn("10");

		gangs    = new FakeGangLookup();
		users    = new FakeUserLookup();
		garrisons = mock(GarrisonManager.class);
		when(garrisons.count(anyInt())).thenReturn(4);

		TurfMessageContract legacy = mock(TurfMessageContract.class);
		when(legacy.formatDuration(anyLong())).thenReturn("5m");
		TurfMessages text = mock(TurfMessages.class, invocation -> {
			if ("reason".equals(invocation.getMethod().getName())) {
				return "reason";
			}
			Object[] args  = invocation.getArguments();
			String   value = (String) args[1];
			for (int i = 2; i + 1 < args.length; i += 2) {
				value = value.replace("%" + args[i] + "%", String.valueOf(args[i + 1]));
			}
			return value;
		});
		TurfSettings turfSettings = mock(TurfSettings.class);
		turfManager = mock(TurfManager.class);
		service = new TurfMapService(turfManager, gangs, users,
		                             mock(CaptureEligibility.class), mock(TurfMapSettings.class), turfSettings,
		                             text, legacy, garrisons);
	}

	@AfterEach
	void tearDown() {
		settings.close();
	}

	private static Gang gang(int id, long lastOnline, String name) {
		Gang gang = CaptureFixtures.gang(id);
		when(gang.getName()).thenReturn(name);
		when(gang.getDisplayName()).thenReturn(name);
		when(gang.getLastMemberOnlineAt()).thenReturn(lastOnline);
		return gang;
	}

	private static Turf turf(int id, Integer owner) {
		return new Turf(id, "Docks", new CuboidRegion("world", 0, 0, 15, 15), owner, BigDecimal.TEN, 0L, 0L);
	}

	private static Player viewer(UUID uuid) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);
		return player;
	}

	private static TerritoryRaster.Cell cell(char glyph, TurfRelation relation, int turfId) {
		return new TerritoryRaster.Cell(glyph, relation, TerritoryRaster.Style.PLAIN, turfId);
	}

	private static TerritoryRaster.Raster oneRow() {
		TerritoryRaster.Cell[] row = new TerritoryRaster.Cell[11];
		for (int i = 0; i < 9; i++) {
			row[i] = cell('K', TurfRelation.ALLY, 3);
		}
		row[9] = cell('-', null, -1);
		row[10] = cell('-', null, -1);
		return new TerritoryRaster.Raster(new TerritoryRaster.Cell[][]{row}, List.of(), 1, -1);
	}

	private static List<BaseComponent[]> lines(TerritoryRaster.Raster raster) {
		return new TurfMapRenderer().lines("header", raster, id -> "hover " + id, "you", List.of());
	}

	private static List<TextComponent> children(BaseComponent[] line) {
		List<TextComponent> out = new java.util.ArrayList<>();
		for (BaseComponent component : line) {
			out.add((TextComponent) component);
		}
		return out;
	}

	@Test
	@DisplayName("a row segment of nine K cells is ONE component, clicked to the turf info card")
	void runLength_mergesAndClicks() {
		List<TextComponent> row = children(lines(oneRow()).get(1));

		TextComponent run = row.stream().filter(c -> c.getText().equals("KKKKKKKKK")).findFirst().orElse(null);
		assertNotNull(run);
		assertEquals(ClickEvent.Action.RUN_COMMAND, run.getClickEvent().getAction());
		assertEquals("/glw turf info 3", run.getClickEvent().getValue());
	}

	@Test
	@DisplayName("dash runs carry no hover and no click")
	void dashRuns_noHoverNoClick() {
		List<TextComponent> row = children(lines(oneRow()).get(1));

		TextComponent dashes = row.stream().filter(c -> c.getText().equals("--")).findFirst().orElse(null);
		assertNotNull(dashes);
		assertNull(dashes.getHoverEvent());
		assertNull(dashes.getClickEvent());
	}

	@Test
	@DisplayName("no component of the grid is bold")
	void noBold() {
		for (BaseComponent[] line : lines(oneRow())) {
			for (TextComponent component : children(line)) {
				assertFalse(component.isBold());
			}
		}
	}

	@Test
	@DisplayName("the owners-only guards line is absent for a rival viewer and present for the owning gang")
	void guards_ownersOnly() {
		gangs.register(gang(1, NOW, "Vipers"));
		gangs.register(gang(2, NOW, "Rats"));
		Turf docks = turf(7, 1);

		String asRival = service.hoverText(viewer(UUID.randomUUID()), docks, 2, NOW);
		String asOwner = service.hoverText(viewer(UUID.randomUUID()), docks, 1, NOW);

		assertFalse(asRival.contains("guards"));
		assertTrue(asOwner.contains("guards"));
	}

	@Test
	@DisplayName("the own-gang-only online line is absent for an ally viewer and present for the owning gang")
	void onlineLine_ownGangOnly() {
		gangs.register(gang(1, NOW - 180_000L, "Vipers"));
		gangs.register(gang(3, NOW, "Kings"));
		Turf docks = turf(7, 1);

		String asAlly  = service.hoverText(viewer(UUID.randomUUID()), docks, 3, NOW);
		String asOwner = service.hoverText(viewer(UUID.randomUUID()), docks, 1, NOW);

		assertFalse(asAlly.contains("Last member"));
		assertFalse(asAlly.contains("online now"));
		assertTrue(asOwner.contains("Last member"));
	}

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	@Test
	@DisplayName("a turf hover built from the shipped turf_messages.yml starts with the name, not a raw '&f'")
	void hover_realMessages_noRawAmpersand() throws Exception {
		FileManager fileManager = YamlFixture.fileManager(tempDir, "turf_messages",
		                                                  YamlFixture.shipped("/turf/turf_messages.yml"));
		TurfMessageContract legacy = mock(TurfMessageContract.class);
		when(legacy.formatDuration(anyLong())).thenReturn("5m");
		TurfMessages real = new TurfMessages(fileManager, legacy,
		                                     CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90));
		CaptureEligibility eligibility = mock(CaptureEligibility.class);
		when(eligibility.evaluate(any(), anyLong()))
				.thenReturn(new CaptureEligibility.Verdict(CaptureEligibility.Reason.ATTACKABLE, 0L));
		gangs.register(gang(1, NOW, "Vipers"));
		TurfMapService realService = new TurfMapService(mock(TurfManager.class), gangs, users, eligibility,
		                                          mock(TurfMapSettings.class), mock(TurfSettings.class), real, legacy,
		                                          garrisons);

		String hover = realService.hoverText(viewer(UUID.randomUUID()), turf(7, null), null, NOW);

		assertFalse(hover.contains("&f"));
		assertTrue(hover.startsWith("\u00A7fDocks"));
	}

	@Test
	@DisplayName("a text row of a turf under capture says 'under attack' with the challenger, as the HUD and hover do")
	void textReason_contestingTurf_underAttack() {
		TurfRuntimeState state = new TurfRuntimeState(7);
		state.setState(TurfState.CONTESTING);
		state.setChallengerGangId(2);
		state.setCaptureProgress(40.0);
		when(turfManager.getRuntimeState(7)).thenReturn(state);
		gangs.register(gang(2, NOW, "Vipers"));

		String reason = service.textReason(turf(7, 1), "Gamma", NOW);

		assertTrue(reason.contains("under attack"), reason);
		assertTrue(reason.contains("Vipers"), reason);
	}

	@Test
	@DisplayName("a quiet turf's text row keeps the eligibility reason")
	void textReason_quietTurf_eligibilityReason() {
		when(turfManager.getRuntimeState(7)).thenReturn(new TurfRuntimeState(7));

		assertEquals("reason", service.textReason(turf(7, 1), "Gamma", NOW));
	}
}
