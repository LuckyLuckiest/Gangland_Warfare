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

/**
 * The glyph a gang gets when its names run out of free letters: display-name letters first, then ASCII-name letters,
 * then A-Z without I, then 2-9, and 9 again once every digit is taken.
 */
@DisplayName("TerritoryRaster - glyph allocation fallbacks")
class TerritoryRasterGlyphFallbackTest {

	private static final int WIDTH  = 41;
	private static final int HEIGHT = 7;
	private static final int CELL   = 16;

	private static CuboidRegion cell(int cx, int cz) {
		return new CuboidRegion("world", cx * CELL, cz * CELL, cx * CELL + CELL - 1, cz * CELL + CELL - 1);
	}

	private static TerritoryRaster.Input turf(int id, CuboidRegion region, int owner) {
		return new TerritoryRaster.Input(id, "Turf " + id, region, TurfRelation.RIVAL, owner, false, false);
	}

	private static TerritoryRaster.GangName gang(int id, String displayName, String name) {
		return new TerritoryRaster.GangName(id, displayName, name, TurfRelation.RIVAL);
	}

	@Test
	@DisplayName("a gang whose display letters are taken uses its name's letters, then A-Z, skipping taken ones")
	void displayTaken_nameTaken_alphabet() {
		List<TerritoryRaster.Input> turfs = List.of(turf(1, cell(-3, -3), 1), turf(2, cell(-2, -3), 2),
		                                            turf(3, cell(-1, -3), 3));
		Map<Integer, TerritoryRaster.GangName> gangs = new LinkedHashMap<>();
		gangs.put(1, gang(1, "Rats", "Rats"));
		gangs.put(2, gang(2, "R", "Rats"));
		gangs.put(3, gang(3, "R", "R"));

		var raster = TerritoryRaster.render(8, 8, WIDTH, HEIGHT, CELL, turfs, gangs, null);

		assertEquals('R', raster.cells()[0][17].glyph());
		assertEquals('A', raster.cells()[0][18].glyph(), "display R taken, name Rats: R taken, so A");
		assertEquals('B', raster.cells()[0][19].glyph(), "display R and name R taken, A taken, so B");
	}

	@Test
	@DisplayName("34 nameless gangs get A-Z without I, then 2-9, and the 34th reuses 9")
	void lettersThenDigitsThenNine() {
		List<TerritoryRaster.Input>            turfs = new ArrayList<>();
		Map<Integer, TerritoryRaster.GangName> gangs = new LinkedHashMap<>();
		for (int i = 1; i <= 34; i++) {
			int col = i - 1;
			turfs.add(turf(i, new CuboidRegion("world", (col - 20) * CELL, -48, (col - 20) * CELL + 15, -33), i));
			gangs.put(i, gang(i, "", ""));
		}

		var raster = TerritoryRaster.render(8, 8, WIDTH, HEIGHT, CELL, turfs, gangs, null);

		String glyphs = raster.legend().stream().map(entry -> String.valueOf(entry.glyph()))
		                      .reduce("", String::concat);
		assertEquals("ABCDEFGHJKLMNOPQRSTUVWXYZ" + "23456789" + "9", glyphs);
	}
}
