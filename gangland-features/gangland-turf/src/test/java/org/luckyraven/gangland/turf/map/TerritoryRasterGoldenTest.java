package org.luckyraven.gangland.turf.map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.capture.CaptureSettings;
import org.luckyraven.gangland.turf.capture.TurfRelation;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.YamlFixture;

import java.math.BigDecimal;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reproduces the sample frame of PLAN section 5.2 from fixture turfs: the viewer is Vipers at block (8, 8), Kings are
 * allied, Rats are rivals and attackable, Saints are rivals shielded by an owner online now, and Kings are under attack.
 * Timestamps come from the configured minutes, never from literal defaults.
 */
@DisplayName("TerritoryRaster golden frame - the PLAN 5.2 sample")
class TerritoryRasterGoldenTest {

	private static final long NOW        = 10_000_000_000L;
	private static final int  VIPERS     = 1;
	private static final int  KINGS      = 2;
	private static final int  RATS       = 3;
	private static final int  SAINTS     = 4;
	private static final int  CAPTURE    = 60;
	private static final int  COOLDOWN   = 15;
	private static final int  PROTECTION = 10;

	private FakeGangLookup gangs;
	private CaptureEligibility eligibility;
	private CaptureSettings settings;

	@BeforeEach
	void setUp() {
		settings = CaptureFixtures.settings(CAPTURE, COOLDOWN, 10, PROTECTION, new int[]{25, 50, 75}, 30, 30);
		gangs    = new FakeGangLookup();

		Gang vipers = gang(VIPERS, NOW);
		Gang kings  = gang(KINGS, NOW);
		Gang rats   = gang(RATS, NOW - (settings.getPostLogoffProtectionMinutes() + 5) * 60_000L);
		Gang saints = gang(SAINTS, NOW);
		when(kings.isAlly(any())).thenReturn(true);
		gangs.register(vipers);
		gangs.register(kings);
		gangs.register(rats);
		gangs.register(saints);
		eligibility = new CaptureEligibility(gangs, settings);
	}

	private static Gang gang(int id, long lastOnline) {
		Gang gang = CaptureFixtures.gang(id);
		when(gang.getLastMemberOnlineAt()).thenReturn(lastOnline);
		when(gang.getName()).thenReturn("Gang" + id);
		when(gang.getDisplayName()).thenReturn(name(id));
		return gang;
	}

	private static String name(int id) {
		return switch (id) {
			case VIPERS -> "Vipers";
			case KINGS -> "Kings";
			case RATS -> "Rats";
			default -> "Saints";
		};
	}

	/** Columns c1..c2 and rows r1..r2 of the 41 x 7 grid as a block square (grid x = column - 20, grid z = row - 3). */
	private static CuboidRegion area(int c1, int r1, int c2, int r2) {
		return new CuboidRegion("world", (c1 - 20) * 16, (r1 - 3) * 16, (c2 - 20) * 16 + 15, (r2 - 3) * 16 + 15);
	}

	private static Turf turf(int id, String name, CuboidRegion region, Integer owner) {
		return new Turf(id, name, region, owner, BigDecimal.TEN, 0L, 0L);
	}

	private TerritoryRaster.Raster frame() {
		List<Turf> turfs = List.of(
				turf(1, "Rats", area(2, 0, 9, 3), RATS),
				turf(2, "North", area(11, 0, 17, 1), null),
				turf(3, "Kings", area(24, 1, 32, 4), KINGS),
				turf(4, "Saints", area(34, 2, 39, 5), SAINTS),
				turf(5, "Vipers", area(14, 3, 22, 6), VIPERS),
				turf(6, "South", area(25, 6, 31, 6), null));

		List<TerritoryRaster.Input> inputs = new ArrayList<>();
		for (Turf turf : turfs) {
			TurfRelation relation = TurfRelation.of(turf, VIPERS, gangs);
			boolean underAttack = turf.getId() == 3;
			boolean shielded    = (relation == TurfRelation.RIVAL || relation == TurfRelation.UNCLAIMED)
			                      && !eligibility.evaluate(turf, NOW).attackable();
			inputs.add(new TerritoryRaster.Input(turf.getId(), turf.getDisplayName(), turf.getRegion(), relation,
			                                     turf.getOwnerGangId(), underAttack, shielded));
		}

		Map<Integer, TerritoryRaster.GangName> names = new LinkedHashMap<>();
		for (int id : new int[]{VIPERS, KINGS, RATS, SAINTS}) {
			names.put(id, new TerritoryRaster.GangName(id, name(id), "Gang" + id, null));
		}
		return TerritoryRaster.render(8, 8, 41, 7, 16, inputs, names, VIPERS);
	}

	private static String row(TerritoryRaster.Cell[] cells) {
		StringBuilder line = new StringBuilder();
		for (TerritoryRaster.Cell cell : cells) {
			line.append(cell.glyph());
		}
		return line.toString();
	}

	@Test
	@DisplayName("every grid row is the sample, exactly 41 glyphs wide")
	void gridRows_matchTheSample() {
		var raster = frame();

		assertEquals("--RRRRRRRR-#######-----------------------", row(raster.cells()[0]));
		assertEquals("--RRRRRRRR-#######------KKKKKKKKK--------", row(raster.cells()[1]));
		assertEquals("--RRRRRRRR--------------KKKKKKKKK-SSSSSS-", row(raster.cells()[2]));
		assertEquals("--RRRRRRRR----VVVVVV+VV-KKKKKKKKK-SSSSSS-", row(raster.cells()[3]));
		assertEquals("--------------VVVVVVVVV-KKKKKKKKK-SSSSSS-", row(raster.cells()[4]));
		assertEquals("--------------VVVVVVVVV-----------SSSSSS-", row(raster.cells()[5]));
		assertEquals("--------------VVVVVVVVV--#######---------", row(raster.cells()[6]));
	}

	@Test
	@DisplayName("the Kings block is italic (under attack) and the Saints block is underlined (shielded)")
	void styles_kingsItalic_saintsUnderlined() {
		var raster = frame();

		assertEquals(TerritoryRaster.Style.ITALIC, raster.cells()[1][24].style());
		assertEquals(TerritoryRaster.Style.ITALIC, raster.cells()[4][32].style());
		assertEquals(TerritoryRaster.Style.UNDERLINE, raster.cells()[2][34].style());
		assertEquals(TerritoryRaster.Style.PLAIN, raster.cells()[0][2].style());
		assertEquals(TerritoryRaster.Style.PLAIN, raster.cells()[0][11].style());
	}

	@Test
	@DisplayName("the plus is column 20 of row 3, on Vipers' turf")
	void viewerIsPlusOnOwnTurf() {
		var raster = frame();

		assertEquals('+', raster.cells()[3][20].glyph());
		assertEquals(5, raster.viewerTurfId());
	}

	@Test
	@DisplayName("legend line 1 packs three gangs and ends with +1 more: Saints would overflow 300 px")
	void legendLineOne_matchesTheSample() {
		var raster = frame();
		LegendLine.Packed packed = LegendLine.pack(raster.legend(),
		                                           entry -> LegendLine.label(entry, "(you)", "(ally)", "(rival)"),
		                                           count -> "+" + count + " more");
		String line = packed.shown()
				.stream()
				.map(entry -> LegendLine.label(entry, "(you)", "(ally)", "(rival)"))
				.collect(Collectors.joining("  "));
		if (!packed.hidden().isEmpty()) {
			line += "  +" + packed.hidden().size() + " more";
		}

		assertEquals("V Vipers (you)  K Kings (ally)  R Rats (rival)  +1 more", line);
		assertTrue(PixelWidth.of(line) <= LegendLine.MAX_PIXELS);
	}

	@Test
	@DisplayName("legend line 2 is the shipped key with its colour codes stripped")
	void legendLineTwo_isTheShippedKey() throws Exception {
		String yaml = YamlFixture.shipped("/turf/turf_messages.yml");
		String key  = YamlConfiguration.loadConfiguration(new StringReader(yaml)).getString("Map.Legend_Key");

		assertEquals("# unclaimed  - none  italic attacked  underlined shielded",
		             key.replaceAll("[&\u00A7][0-9a-fk-orxA-FK-ORX]", ""));
	}
}
