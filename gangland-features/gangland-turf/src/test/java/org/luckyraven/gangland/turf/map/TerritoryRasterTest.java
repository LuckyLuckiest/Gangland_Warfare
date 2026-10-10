package org.luckyraven.gangland.turf.map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.capture.TurfRelation;
import org.luckyraven.gangland.turf.data.CuboidRegion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The pure cell rules of {@link TerritoryRaster}: majority share, centre-cell guarantee, viewer cell, north-up rows
 * and the glyph allocation. The viewer block (8, 8) sits in grid cell (0, 0), which is column 20 and row 3 of a
 * 41 x 7 grid.
 */
@DisplayName("TerritoryRaster - majority, centre guarantee, glyphs and styles")
class TerritoryRasterTest {

	private static final int WIDTH  = 41;
	private static final int HEIGHT = 7;
	private static final int CELL   = 16;

	/** The block square of grid cells cxFrom..cxTo x czFrom..czTo (grid cell (0, 0) is the viewer's cell). */
	private static CuboidRegion cells(int cxFrom, int czFrom, int cxTo, int czTo) {
		return new CuboidRegion("world", cxFrom * CELL, czFrom * CELL, cxTo * CELL + CELL - 1, czTo * CELL + CELL - 1);
	}

	private static CuboidRegion blocks(int x1, int z1, int x2, int z2) {
		return new CuboidRegion("world", x1, z1, x2, z2);
	}

	private static TerritoryRaster.Input turf(int id, CuboidRegion region, TurfRelation relation, Integer owner) {
		return new TerritoryRaster.Input(id, "Turf " + id, region, relation, owner, false, false);
	}

	private static TerritoryRaster.Input turf(int id, CuboidRegion region, TurfRelation relation, Integer owner,
	                                          boolean underAttack, boolean shielded) {
		return new TerritoryRaster.Input(id, "Turf " + id, region, relation, owner, underAttack, shielded);
	}

	private static TerritoryRaster.GangName gang(int id, String displayName, String name, TurfRelation relation) {
		return new TerritoryRaster.GangName(id, displayName, name, relation);
	}

	private static Map<Integer, TerritoryRaster.GangName> gangs(TerritoryRaster.GangName... list) {
		Map<Integer, TerritoryRaster.GangName> map = new LinkedHashMap<>();
		for (TerritoryRaster.GangName gang : list) {
			map.put(gang.id(), gang);
		}
		return map;
	}

	private static TerritoryRaster.Raster render(List<TerritoryRaster.Input> turfs,
	                                             Map<Integer, TerritoryRaster.GangName> gangs, Integer viewerGang) {
		return TerritoryRaster.render(8, 8, WIDTH, HEIGHT, CELL, turfs, gangs, viewerGang);
	}

	/** Grid cell (cx, cz) relative to the viewer cell: column cx + 20, row cz + 3. */
	private static TerritoryRaster.Cell at(TerritoryRaster.Raster raster, int cx, int cz) {
		return raster.cells()[cz + 3][cx + 20];
	}

	@Test
	@DisplayName("a turf covering half of a cell (128 of 256 blocks) takes the cell")
	void majority_halfCoveredCellShowsTheTurf() {
		var raster = render(List.of(turf(1, blocks(16, 0, 31, 7), TurfRelation.RIVAL, 2)),
		                    gangs(gang(2, "Rats", "Rats", TurfRelation.RIVAL)), null);

		assertEquals('R', at(raster, 1, 0).glyph());
	}

	@Test
	@DisplayName("a turf covering less than half of a cell (120 of 256 blocks) does not take it")
	void lessThanHalf_isNone() {
		var raster = render(List.of(turf(1, blocks(16, 0, 30, 7), TurfRelation.RIVAL, 2)),
		                    gangs(gang(2, "Rats", "Rats", TurfRelation.RIVAL)), null);

		assertEquals('-', at(raster, 1, 0).glyph());
	}

	@Test
	@DisplayName("when two turfs share a cell equally the lower turf id wins")
	void largestShareWins_tieGoesToLowerId() {
		var raster = render(List.of(turf(5, blocks(16, 0, 31, 7), TurfRelation.RIVAL, 1),
		                            turf(2, blocks(16, 8, 31, 15), TurfRelation.RIVAL, 3)),
		                    gangs(gang(1, "Alpha", "Alpha", TurfRelation.RIVAL),
		                          gang(3, "Bravo", "Bravo", TurfRelation.RIVAL)), null);

		assertEquals('B', at(raster, 1, 0).glyph());
		assertEquals(2, at(raster, 1, 0).turfId());
	}

	@Test
	@DisplayName("the turf with the larger share wins even with the higher id")
	void largestShareWins_evenWithHigherId() {
		var raster = render(List.of(turf(9, blocks(16, 0, 31, 9), TurfRelation.RIVAL, 1),
		                            turf(1, blocks(16, 10, 31, 15), TurfRelation.RIVAL, 3)),
		                    gangs(gang(1, "Alpha", "Alpha", TurfRelation.RIVAL),
		                          gang(3, "Bravo", "Bravo", TurfRelation.RIVAL)), null);

		assertEquals('A', at(raster, 1, 0).glyph());
	}

	@Test
	@DisplayName("a small turf that wins no cell is drawn in the cell holding its centre")
	void smallTurf_drawnInItsCentreCell() {
		// 4 x 4 blocks straddling four cells: none has a majority; the centre block (31, 31) is in cell (1, 1)
		var raster = render(List.of(turf(7, blocks(30, 30, 33, 33), TurfRelation.RIVAL, 2)),
		                    gangs(gang(2, "Rats", "Rats", TurfRelation.RIVAL)), null);

		assertEquals('R', at(raster, 1, 1).glyph());
		assertEquals(7, at(raster, 1, 1).turfId());
		assertEquals('-', at(raster, 0, 0).glyph());
	}

	@Test
	@DisplayName("the viewer's cell is a plus and the turf covering the viewer's block is recorded")
	void viewerCell_isPlus_andViewerTurfIdRecorded() {
		var raster = render(List.of(turf(4, cells(0, 0, 0, 0), TurfRelation.OWN, 1)),
		                    gangs(gang(1, "Vipers", "Vipers", TurfRelation.OWN)), 1);

		assertEquals('+', at(raster, 0, 0).glyph());
		assertNull(at(raster, 0, 0).relation());
		assertEquals(4, raster.viewerTurfId());
	}

	@Test
	@DisplayName("north is up: a turf at negative z lands in row 0")
	void fixedNorth_row0IsNorth() {
		var raster = render(List.of(turf(1, blocks(0, -40, 15, -33), TurfRelation.RIVAL, 2)),
		                    gangs(gang(2, "Rats", "Rats", TurfRelation.RIVAL)), null);

		assertEquals('R', raster.cells()[0][20].glyph());
		assertEquals('-', raster.cells()[6][20].glyph());
	}

	@Test
	@DisplayName("glyphs go viewer's gang first, then allies, then ascending id; collisions take the next name letter")
	void glyphs_viewerGangFirst_thenAllies_thenById() {
		var raster = render(List.of(turf(1, cells(-3, -3, -3, -3), TurfRelation.OWN, 7),
		                            turf(2, cells(-2, -3, -2, -3), TurfRelation.ALLY, 9),
		                            turf(3, cells(-1, -3, -1, -3), TurfRelation.RIVAL, 1)),
		                    gangs(gang(7, "Rats", "Rats", TurfRelation.OWN),
		                          gang(9, "Rats", "Rats", TurfRelation.ALLY),
		                          gang(1, "Rats", "Rats", TurfRelation.RIVAL)), 7);

		assertEquals('R', at(raster, -3, -3).glyph());
		assertEquals('A', at(raster, -2, -3).glyph());
		assertEquals('T', at(raster, -1, -3).glyph());
		assertEquals(7, raster.legend().get(0).gangId());
		assertEquals(9, raster.legend().get(1).gangId());
		assertEquals(1, raster.legend().get(2).gangId());
	}

	@Test
	@DisplayName("a display name that collides takes the next letter of its ASCII name")
	void glyphs_collisionTakesNextLetterOfName() {
		var raster = render(List.of(turf(1, cells(-3, -3, -3, -3), TurfRelation.RIVAL, 1),
		                            turf(2, cells(-2, -3, -2, -3), TurfRelation.RIVAL, 2)),
		                    gangs(gang(1, "Rats", "Rats", TurfRelation.RIVAL),
		                          gang(2, "Rum", "Rum", TurfRelation.RIVAL)), null);

		assertEquals('R', at(raster, -3, -3).glyph());
		assertEquals('U', at(raster, -2, -3).glyph());
	}

	@Test
	@DisplayName("the letter I is never used: gang Ivy is V")
	void glyphs_neverI() {
		var raster = render(List.of(turf(1, cells(-3, -3, -3, -3), TurfRelation.RIVAL, 1)),
		                    gangs(gang(1, "Ivy", "Ivy", TurfRelation.RIVAL)), null);

		assertEquals('V', at(raster, -3, -3).glyph());
	}

	@Test
	@DisplayName("after the 25 letters the 26th gang gets digit 2")
	void glyphs_afterTwentyFiveLettersUseDigits() {
		List<TerritoryRaster.Input>                turfs  = new ArrayList<>();
		Map<Integer, TerritoryRaster.GangName> gangs  = new LinkedHashMap<>();
		for (int i = 1; i <= 26; i++) {
			int col = i - 1;
			turfs.add(turf(i, blocks((col - 20) * CELL, -48, (col - 20) * CELL + 15, -33), TurfRelation.RIVAL, i));
			gangs.put(i, gang(i, "", "", TurfRelation.RIVAL));
		}
		var raster = render(turfs, gangs, null);

		assertEquals(26, raster.legend().size());
		assertEquals('Z', raster.legend().get(24).glyph());
		assertEquals('2', raster.legend().get(25).glyph());
	}

	@Test
	@DisplayName("a Cyrillic display name yields its ASCII name's letter")
	void glyph_cyrillicDisplayName_usesAsciiName() {
		var raster = render(List.of(turf(1, cells(-3, -3, -3, -3), TurfRelation.RIVAL, 1)),
		                    gangs(gang(1, "Крысы", "Rats", TurfRelation.RIVAL)), null);

		assertEquals('R', at(raster, -3, -3).glyph());
	}

	@Test
	@DisplayName("a CJK display name yields its ASCII name's letter")
	void glyph_cjkDisplayName_usesAsciiName() {
		var raster = render(List.of(turf(1, cells(-3, -3, -3, -3), TurfRelation.RIVAL, 1)),
		                    gangs(gang(1, "猫王", "Kings", TurfRelation.RIVAL)), null);

		assertEquals('K', at(raster, -3, -3).glyph());
	}

	@Test
	@DisplayName("a hex-coloured display name is stripped before its letters are read")
	void glyph_hexColouredDisplayName_strippedFirst() {
		var raster = render(List.of(turf(1, cells(-3, -3, -3, -3), TurfRelation.RIVAL, 1)),
		                    gangs(gang(1, "&#FF0000R&#00FF00ats", "Rats", TurfRelation.RIVAL)), null);

		assertEquals('R', at(raster, -3, -3).glyph());
	}

	@Test
	@DisplayName("a gang with no ASCII letter anywhere takes the first free letter")
	void glyph_noLetterAnywhere_takesFreeLetter() {
		var raster = render(List.of(turf(1, cells(-3, -3, -3, -3), TurfRelation.RIVAL, 1)),
		                    gangs(gang(1, "???", "123", TurfRelation.RIVAL)), null);

		assertEquals('A', at(raster, -3, -3).glyph());
	}

	@Test
	@DisplayName("each relation reaches its cell unchanged; an unclaimed cell is a hash")
	void relationColours() {
		var raster = render(List.of(turf(1, cells(-3, -3, -3, -3), TurfRelation.OWN, 7),
		                            turf(2, cells(-2, -3, -2, -3), TurfRelation.ALLY, 9),
		                            turf(3, cells(-1, -3, -1, -3), TurfRelation.RIVAL, 1),
		                            turf(4, cells(0, -3, 0, -3), TurfRelation.GANGLESS, 2),
		                            turf(5, cells(1, -3, 1, -3), TurfRelation.UNCLAIMED, null)),
		                    gangs(gang(7, "Vipers", "Vipers", TurfRelation.OWN),
		                          gang(9, "Kings", "Kings", TurfRelation.ALLY),
		                          gang(1, "Rats", "Rats", TurfRelation.RIVAL),
		                          gang(2, "Saints", "Saints", TurfRelation.GANGLESS)), 7);

		assertEquals(TurfRelation.OWN, at(raster, -3, -3).relation());
		assertEquals(TurfRelation.ALLY, at(raster, -2, -3).relation());
		assertEquals(TurfRelation.RIVAL, at(raster, -1, -3).relation());
		assertEquals(TurfRelation.GANGLESS, at(raster, 0, -3).relation());
		assertEquals(TurfRelation.UNCLAIMED, at(raster, 1, -3).relation());
		assertEquals('#', at(raster, 1, -3).glyph());
	}

	@Test
	@DisplayName("under attack is italic, shielded is underlined, both is both, neither is plain")
	void styles() {
		var raster = render(List.of(turf(1, cells(-3, -3, -3, -3), TurfRelation.RIVAL, 1, true, false),
		                            turf(2, cells(-2, -3, -2, -3), TurfRelation.RIVAL, 2, false, true),
		                            turf(3, cells(-1, -3, -1, -3), TurfRelation.RIVAL, 3, true, true),
		                            turf(4, cells(0, -3, 0, -3), TurfRelation.RIVAL, 4, false, false)),
		                    gangs(gang(1, "Rats", "Rats", TurfRelation.RIVAL),
		                          gang(2, "Kings", "Kings", TurfRelation.RIVAL),
		                          gang(3, "Saints", "Saints", TurfRelation.RIVAL),
		                          gang(4, "Vipers", "Vipers", TurfRelation.RIVAL)), null);

		assertEquals(TerritoryRaster.Style.ITALIC, at(raster, -3, -3).style());
		assertEquals(TerritoryRaster.Style.UNDERLINE, at(raster, -2, -3).style());
		assertEquals(TerritoryRaster.Style.ITALIC_UNDERLINE, at(raster, -1, -3).style());
		assertEquals(TerritoryRaster.Style.PLAIN, at(raster, 0, -3).style());
	}

	@Test
	@DisplayName("the content hash changes when a style changes and is stable otherwise")
	void contentHash_changesWhenAStyleChanges_stableOtherwise() {
		var gangs  = gangs(gang(1, "Rats", "Rats", TurfRelation.RIVAL));
		var plain  = render(List.of(turf(1, cells(-3, -3, -3, -3), TurfRelation.RIVAL, 1)), gangs, null);
		var again  = render(List.of(turf(1, cells(-3, -3, -3, -3), TurfRelation.RIVAL, 1)), gangs, null);
		var italic = render(List.of(turf(1, cells(-3, -3, -3, -3), TurfRelation.RIVAL, 1, true, false)), gangs, null);

		assertEquals(plain.contentHash(), again.contentHash());
		assertNotEquals(plain.contentHash(), italic.contentHash());
	}
}
