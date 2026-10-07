package org.luckyraven.gangland.copsncrooks.setup;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The per-admin wand selection: corners, the world-change reset, which modes are single-point, the mode tags. */
@DisplayName("SetupSelection")
class SetupSelectionTest {

	private final World west = SetupFixture.world("west");
	private final World east = SetupFixture.world("east");

	@Test
	@DisplayName("left click is pos1 and right click is pos2")
	void corners_arePos1AndPos2() {
		SetupSelection selection = new SetupSelection();
		Location       a         = new Location(west, 1, 2, 3);
		Location       b         = new Location(west, 4, 5, 6);

		selection.set(a, true);
		selection.set(b, false);

		assertSame(a, selection.getPos1());
		assertSame(b, selection.getPos2());
		assertEquals("west", selection.getWorld());
	}

	@Test
	@DisplayName("a click in another world drops the corner of the first")
	void worldChange_resetsTheOtherCorner() {
		SetupSelection selection = new SetupSelection();
		selection.set(new Location(west, 1, 2, 3), true);

		selection.set(new Location(east, 9, 9, 9), false);

		assertNull(selection.getPos1());
		assertEquals("east", selection.getWorld());
		assertFalse(selection.isComplete());
	}

	@Test
	@DisplayName("point modes are complete with pos1 alone, cuboid modes need both corners")
	void pointModes_needPos1Only() {
		SetupSelection selection = new SetupSelection();
		selection.set(new Location(west, 1, 2, 3), true);

		selection.setMode(SetupMode.STATION);
		assertTrue(selection.isComplete());
		selection.setMode(SetupMode.PICKUP);
		assertTrue(selection.isComplete());
		selection.setMode(SetupMode.DISTRICT);
		assertFalse(selection.isComplete());
		selection.setMode(SetupMode.BREAKER);
		assertFalse(selection.isComplete());

		selection.set(new Location(west, 4, 5, 6), false);
		assertTrue(selection.isComplete());
	}

	@Test
	@DisplayName("modes map to the C15 region tags and parse case-insensitively")
	void modes_carryTheirTag() {
		assertNull(SetupMode.STATION.getTag());
		assertNull(SetupMode.PICKUP.getTag());
		assertEquals("district", SetupMode.DISTRICT.getTag());
		assertEquals("hideout", SetupMode.HIDEOUT.getTag());
		assertEquals("restricted", SetupMode.RESTRICTED.getTag());
		assertEquals("breaker", SetupMode.BREAKER.getTag());
		assertSame(SetupMode.HIDEOUT, SetupMode.byName("Hideout"));
		assertNull(SetupMode.byName("nonsense"));
	}

	@Test
	@DisplayName("the manager hands out one selection per admin and forgets it on clear")
	void manager_isPerAdmin_andClears() {
		SetupSelections selections = new SetupSelections();
		UUID            id         = UUID.randomUUID();

		SetupSelection first = selections.get(id);

		assertSame(first, selections.get(id));
		selections.clear(id);
		assertNull(selections.peek(id));
	}
}
