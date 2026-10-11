package org.luckyraven.gangland.gang.safehouse;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** What {@link SafeSpot} accepts as a place to stand. Feet at y = 64, head at 65, floor at 63. */
@DisplayName("SafeSpot")
class SafeSpotTest {

	private final World world = mock(World.class);

	private MockedStatic<Bukkit> bukkit;

	@BeforeEach
	void setUp() {
		// XMaterial reads the server version when it first loads
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getBukkitVersion).thenReturn("1.16.5-R0.1-SNAPSHOT");
		bukkit.when(Bukkit::getVersion).thenReturn("git-Spigot-test (MC: 1.16.5)");
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private Block block(Material type, boolean passable, boolean liquid) {
		Block block = mock(Block.class);
		when(block.getType()).thenReturn(type);
		when(block.isPassable()).thenReturn(passable);
		when(block.isLiquid()).thenReturn(liquid);
		return block;
	}

	private Block air() {
		return block(Material.AIR, true, false);
	}

	private Block stone() {
		return block(Material.STONE, false, false);
	}

	private boolean at(int y, Block floor, Block feet, Block head) {
		when(world.getBlockAt(5, y - 1, 5)).thenReturn(floor);
		when(world.getBlockAt(5, y, 5)).thenReturn(feet);
		when(world.getBlockAt(5, y + 1, 5)).thenReturn(head);
		return SafeSpot.isSafe(new Location(world, 5.5, y, 5.5));
	}

	@Test
	@DisplayName("solid floor, two open blocks: safe")
	void plainGround() {
		assertTrue(at(64, stone(), air(), air()));
	}

	@Test
	@DisplayName("lava underfoot or in the body space: refused")
	void lava() {
		assertFalse(at(64, block(Material.LAVA, true, true), air(), air()));
		assertFalse(at(64, stone(), block(Material.LAVA, true, true), air()));
		assertFalse(at(64, stone(), air(), block(Material.LAVA, true, true)));
	}

	@Test
	@DisplayName("fire, magma and a cactus: refused")
	void otherHarm() {
		assertFalse(at(64, stone(), block(Material.FIRE, true, false), air()));
		assertFalse(at(64, block(Material.MAGMA_BLOCK, false, false), air(), air()));
		assertFalse(at(64, block(Material.CACTUS, false, false), air(), air()));
	}

	@Test
	@DisplayName("void: no floor below the world, or air under the feet: refused")
	void voidBelow() {
		assertFalse(at(0, stone(), air(), air()), "y = 0 has nothing under it");
		assertFalse(at(64, air(), air(), air()), "air under the feet is a drop");
	}

	@Test
	@DisplayName("oneHighGap: a solid block at head height: refused")
	void oneHighGap() {
		assertFalse(at(64, stone(), air(), stone()));
		assertFalse(at(64, stone(), stone(), air()));
	}

	@Test
	@DisplayName("water: standing in water over a floor, or over deep water, is allowed; lava is not")
	void water() {
		Block water = block(Material.WATER, true, true);
		assertTrue(at(64, stone(), water, air()));
		assertTrue(at(64, water, water, air()));
	}

	@Test
	@DisplayName("a location with no world is refused")
	void noWorld() {
		assertFalse(SafeSpot.isSafe(new Location(null, 1, 64, 1)));
	}
}
