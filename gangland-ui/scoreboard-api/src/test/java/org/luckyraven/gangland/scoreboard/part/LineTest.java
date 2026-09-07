package org.luckyraven.gangland.scoreboard.part;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.util.Placeholder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the per-board copy semantics {@link Line} grew for UI-01.
 *
 * <p>Observation #1 (ui-inventory-scoreboard.md) / UI-01: {@code ScoreboardManager.getDriverHandler} handed the
 * live {@code ScoreboardAddon.getLines()} list — and the very same {@code Line} instances — to every board it
 * built. {@code Line#update} advances a rotation cursor, so with N players online the cursor moved N times per
 * tick and every board rendered the same frame; the driver's constructor also appended the title to that shared
 * list once per board created, growing it without bound across joins and reloads. Boards now get a deep copy.
 *
 * <p>The placeholder seam is a one-line lambda rather than a Mockito mock (documentation/TESTING.md §6), and it
 * ignores its player argument, so nothing here needs a Bukkit server.
 */
@DisplayName("Line - board-private copies (UI-01)")
class LineTest {

	/** Identity conversion, so {@code update} exercises only the rotation cursor. */
	private static final Placeholder IDENTITY = (player, text) -> text;

	@Test
	@DisplayName("copy() carries over the interval, board slot and contents")
	void copy_preservesIntervalSlotAndContents() {
		Line original = new Line(20L, 3);
		original.addContent("first");
		original.addContent("second");

		Line copy = original.copy();

		assertNotSame(original, copy);
		assertEquals(20L, copy.getInterval());
		assertEquals(3, copy.getUsedIndex());
		assertEquals("first", copy.getCurrentContent());
	}

	@Test
	@DisplayName("UI-01: advancing a copy's rotation cursor leaves every other copy where it was")
	void copy_rotationCursorIsIndependent() {
		Line original = new Line(20L);
		original.addAllContents(List.of("one", "two", "three"));

		Line first  = original.copy();
		Line second = original.copy();

		first.update(IDENTITY, null);

		assertEquals("two", first.getCurrentContent(), "the updated copy advances");
		assertEquals("one", second.getCurrentContent(), "a sibling board's copy must not move");
		assertEquals("one", original.getCurrentContent(), "the configured line must not move either");
	}

	@Test
	@DisplayName("UI-01: copyAll() yields a fresh list of fresh lines, so a board can append its title safely")
	void copyAll_returnsIndependentListAndLines() {
		Line first = new Line(20L, 0);
		first.addContent("first");
		Line second = new Line(40L, 1);
		second.addContent("second");

		List<Line> configured = List.of(first, second);
		List<Line> copies     = Line.copyAll(configured);

		assertEquals(2, copies.size());
		assertNotSame(first, copies.get(0));
		assertNotSame(second, copies.get(1));

		// A board appends its title to its own list; the configured list must be untouched by that.
		copies.add(new StaticLine());

		assertEquals(2, configured.size());
	}

	@Test
	@DisplayName("copying a StaticLine keeps it static")
	void copy_ofStaticLine_staysStatic() {
		StaticLine original = new StaticLine(2);
		original.addContent("title");

		Line copy = original.copy();

		assertInstanceOf(StaticLine.class, copy);
		assertTrue(copy.isStatic());
		assertEquals(2, copy.getUsedIndex());
	}

}
