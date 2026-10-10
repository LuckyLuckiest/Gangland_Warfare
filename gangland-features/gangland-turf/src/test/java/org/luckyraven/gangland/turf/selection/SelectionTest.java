package org.luckyraven.gangland.turf.selection;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link Selection}: a corner in another world than the other corner is rejected and both existing corners are kept
 * (TF-29). A corner in the same world is accepted.
 */
@DisplayName("Selection - both corners share one world (TF-29)")
class SelectionTest {

	private static Location at(String world, int x, int z) {
		World handle = mock(World.class);
		when(handle.getName()).thenReturn(world);
		return new Location(handle, x, 64, z);
	}

	@Test
	@DisplayName("a corner in another world than the existing corner is rejected and the corner is kept")
	void set_otherWorldRejected_keepsCorner() {
		Selection selection = new Selection();
		assertTrue(selection.set(at("world", 0, 0), true));

		boolean accepted = selection.set(at("world_nether", 5, 5), false);

		assertFalse(accepted);
		assertEquals(0, selection.getPos1().getBlockX());
		assertNull(selection.getPos2());
	}

	@Test
	@DisplayName("a corner in the same world as the other corner is accepted")
	void set_sameWorld_accepted() {
		Selection selection = new Selection();
		selection.set(at("world", 0, 0), true);

		boolean accepted = selection.set(at("world", 9, 9), false);

		assertTrue(accepted);
		assertEquals(9, selection.getPos2().getBlockX());
	}
}
