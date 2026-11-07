package org.luckyraven.gangland.turf.map;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.capture.TurfRelation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The colour of each grid cell: the viewer's mark is white whatever its relation, a cell without a relation is dark
 * grey, and every {@link TurfRelation} has its own colour.
 */
@DisplayName("TurfMapRenderer - the colour of a grid cell")
class TurfMapRendererColourTest {

	private static TerritoryRaster.Cell cell(char glyph, TurfRelation relation) {
		return new TerritoryRaster.Cell(glyph, relation, TerritoryRaster.Style.PLAIN, relation == null ? -1 : 1);
	}

	/** The colour of the single component a one-cell grid row renders to. */
	private static ChatColor colourOf(TerritoryRaster.Cell cell) {
		TerritoryRaster.Raster raster = new TerritoryRaster.Raster(new TerritoryRaster.Cell[][]{{cell}}, List.of(), 0, 0);
		List<BaseComponent[]> lines   = new TurfMapRenderer().lines("header", raster, id -> "hover", "you", List.of());
		BaseComponent[]       row     = lines.get(1);
		assertEquals(1, row.length);
		return ((TextComponent) row[0]).getColor();
	}

	@Test
	@DisplayName("each relation has its colour: own green, ally aqua, rival red, gangless yellow, unclaimed grey")
	void relationColours() {
		assertEquals(ChatColor.GREEN, colourOf(cell('A', TurfRelation.OWN)));
		assertEquals(ChatColor.AQUA, colourOf(cell('B', TurfRelation.ALLY)));
		assertEquals(ChatColor.RED, colourOf(cell('C', TurfRelation.RIVAL)));
		assertEquals(ChatColor.YELLOW, colourOf(cell('D', TurfRelation.GANGLESS)));
		assertEquals(ChatColor.GRAY, colourOf(cell('E', TurfRelation.UNCLAIMED)));
	}

	@Test
	@DisplayName("a cell without a relation is dark grey")
	void noRelation_darkGrey() {
		assertEquals(ChatColor.DARK_GRAY, colourOf(cell('-', null)));
	}

	@Test
	@DisplayName("the viewer's mark is white whatever its relation")
	void viewerMark_white() {
		assertEquals(ChatColor.WHITE, colourOf(cell(TerritoryRaster.VIEWER, null)));
		assertEquals(ChatColor.WHITE, colourOf(cell(TerritoryRaster.VIEWER, TurfRelation.RIVAL)));
		assertEquals(ChatColor.WHITE, colourOf(cell(TerritoryRaster.VIEWER, TurfRelation.OWN)));
	}
}
