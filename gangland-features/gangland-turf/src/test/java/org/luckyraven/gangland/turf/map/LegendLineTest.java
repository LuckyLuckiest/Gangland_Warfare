package org.luckyraven.gangland.turf.map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.capture.TurfRelation;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Packing of the first legend line: gang entries while they fit 300 px, then {@code +N more}. */
@DisplayName("LegendLine - packing the first legend line")
class LegendLineTest {

	private static TerritoryRaster.LegendEntry entry(char glyph, String name, TurfRelation relation) {
		return new TerritoryRaster.LegendEntry(glyph, glyph, name, relation, false, false);
	}

	private static String more(int count) {
		return "+" + count + " more";
	}

	@Test
	@DisplayName("two short entries all fit and nothing is hidden")
	void allEntriesFit() {
		List<TerritoryRaster.LegendEntry> entries = List.of(entry('V', "Vipers", TurfRelation.OWN),
		                                                    entry('K', "Kings", TurfRelation.ALLY));

		LegendLine.Packed packed = LegendLine.pack(entries, e -> LegendLine.label(e, "(you)", "(ally)", "(rival)"),
		                                           LegendLineTest::more);

		assertEquals(2, packed.shown().size());
		assertTrue(packed.hidden().isEmpty());
	}

	@Test
	@DisplayName("entries that overflow are hidden and the line with +N more stays within 300 px")
	void overflowBecomesMore() {
		List<TerritoryRaster.LegendEntry> entries = new ArrayList<>();
		String[] names = {"Vipers", "Kings", "Rats", "Saints", "Wolves", "Gladiators", "Mafia", "Hellhounds"};
		char[] glyphs = {'V', 'K', 'R', 'S', 'W', 'G', 'M', 'H'};
		for (int i = 0; i < names.length; i++) {
			entries.add(entry(glyphs[i], names[i], TurfRelation.RIVAL));
		}

		LegendLine.Packed packed = LegendLine.pack(entries, e -> LegendLine.label(e, "(you)", "(ally)", "(rival)"),
		                                           LegendLineTest::more);

		assertTrue(!packed.hidden().isEmpty());
		assertEquals(entries.size(), packed.shown().size() + packed.hidden().size());
		String line = packed.shown()
				.stream()
				.map(e -> LegendLine.label(e, "(you)", "(ally)", "(rival)"))
				.reduce((a, b) -> a + "  " + b)
				.orElse("") + "  " + more(packed.hidden().size());
		assertTrue(PixelWidth.of(line) <= LegendLine.MAX_PIXELS, "line " + PixelWidth.of(line));
	}

	@Test
	@DisplayName("a name longer than 8 characters is cut to its first 8")
	void namesTruncatedToEight() {
		String label = LegendLine.label(entry('V', "Verylongname", TurfRelation.OWN), "(you)", "(ally)", "(rival)");

		assertEquals("V Verylong (you)", label);
	}

	@Test
	@DisplayName("a cut never splits an emoji surrogate pair in the name")
	void emojiNameNotSplit() {
		String name = "Rat" + "🐀".repeat(6);
		String label = LegendLine.label(entry('R', name, TurfRelation.RIVAL), "(you)", "(ally)", "(rival)");

		assertEquals("R Rat" + "🐀".repeat(5) + " (rival)", label);
	}

	@Test
	@DisplayName("a gangless relation adds no suffix")
	void gangless_noSuffix() {
		String label = LegendLine.label(entry('V', "Vipers", TurfRelation.GANGLESS), "(you)", "(ally)", "(rival)");

		assertEquals("V Vipers", label);
	}
}
