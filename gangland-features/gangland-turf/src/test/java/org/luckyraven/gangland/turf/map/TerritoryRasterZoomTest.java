package org.luckyraven.gangland.turf.map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.capture.TurfRelation;
import org.luckyraven.gangland.turf.data.CuboidRegion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The same 64 x 64 block turf rendered at each zoom size. TerritoryRaster samples a cell at its block square: a cell
 * shows the turf when the turf covers at least half of it, so a turf of whole cells fills 64 / 16 / 4 / 1 cells at
 * 8 / 16 / 32 / 64 blocks per cell. The viewer sits at block (-40, -40), outside the turf at every size, so its plus
 * never hides a turf cell; the 41 x 41 window (viewer centred) holds the whole turf at every size.
 */
@DisplayName("TerritoryRaster at 8/16/32/64 blocks per cell - the same turf, fewer or more cells")
class TerritoryRasterZoomTest {

	private static final int SIZE = 41;

	@Test
	@DisplayName("each cell size covers the same turf with 64, 16, 4 and 1 cells")
	void eachCellSize_coversTheSameTurf() {
		Map<Integer, TerritoryRaster.GangName> gangs = new LinkedHashMap<>();
		gangs.put(2, new TerritoryRaster.GangName(2, "Rats", "Rats", TurfRelation.RIVAL));
		TerritoryRaster.Input turf = new TerritoryRaster.Input(1, "Turf 1",
		                                                       new CuboidRegion("world", 0, 0, 63, 63),
		                                                       TurfRelation.RIVAL, 2, false, false);

		int[] sizes    = {8, 16, 32, 64};
		int[] expected = {64, 16, 4, 1};
		for (int i = 0; i < sizes.length; i++) {
			TerritoryRaster.Raster raster = TerritoryRaster.render(-40, -40, SIZE, SIZE, sizes[i], List.of(turf), gangs,
			                                                       null);
			assertEquals(expected[i], countGlyph(raster, 'R'), "cells showing the turf at " + sizes[i] + " blocks");
		}
	}

	private static int countGlyph(TerritoryRaster.Raster raster, char glyph) {
		int count = 0;
		for (TerritoryRaster.Cell[] row : raster.cells()) {
			for (TerritoryRaster.Cell cell : row) {
				if (cell.glyph() == glyph) {
					count++;
				}
			}
		}
		return count;
	}
}
