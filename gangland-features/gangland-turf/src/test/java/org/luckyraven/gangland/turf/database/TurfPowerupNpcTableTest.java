package org.luckyraven.gangland.turf.database;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.npc.TurfPowerupData;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link TurfPowerupNpcTable#getData}: one row per Quartermaster, the column order the table declares, the world
 * name (or the literal "world" when the location has none), yaw and pitch widened to double.
 */
@DisplayName("TurfPowerupNpcTable - getData row shape")
class TurfPowerupNpcTableTest {

	private final TurfPowerupNpcTable table = new TurfPowerupNpcTable();

	@Test
	@DisplayName("a located NPC writes id, world name, x, y, z, yaw, pitch and the display name")
	void rowCarriesTheLocation() {
		World world = mock(World.class);
		when(world.getName()).thenReturn("gangland_city");
		TurfPowerupData data = new TurfPowerupData(7, new Location(world, 1.5, 64.0, -3.25, 90.0F, -12.5F), "Quartermaster");

		assertArrayEquals(new Object[]{7, "gangland_city", 1.5, 64.0, -3.25, 90.0, -12.5, "Quartermaster"},
		                  table.getData(data));
	}

	@Test
	@DisplayName("a location with no world falls back to the literal world, and a null display name stays null")
	void missingWorldFallsBack() {
		TurfPowerupData data = new TurfPowerupData(8, new Location(null, 0.0, 70.0, 2.0, 0.0F, 0.0F), null);

		assertArrayEquals(new Object[]{8, "world", 0.0, 70.0, 2.0, 0.0, 0.0, null}, table.getData(data));
	}

	@Test
	@DisplayName("an NPC with no spawn location is a NullPointerException, not a row")
	void nullLocationThrows() {
		TurfPowerupData data = new TurfPowerupData(9, null, "Quartermaster");

		assertThrows(NullPointerException.class, () -> table.getData(data));
	}
}
