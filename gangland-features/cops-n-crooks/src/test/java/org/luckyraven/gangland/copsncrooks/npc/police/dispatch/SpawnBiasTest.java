package org.luckyraven.gangland.copsncrooks.npc.police.dispatch;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SpawnBias#lead} (0.21.1, owner ruling R5): the squad's last-known spot led on along the heading, short of a
 * wall, standing on the ground there, facing the heading.
 */
@DisplayName("SpawnBias - the hand-off lead")
class SpawnBiasTest {

	/** A world whose ground top at block column (x, z) is {@code floor(x, z)}: solid below it, air from it up. */
	private static World world(IntBinaryOperator floor) {
		World world = mock(World.class);
		when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
		when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(i -> {
			Block block = mock(Block.class);
			int   top   = floor.applyAsInt(i.getArgument(0), i.getArgument(2));
			when(block.isPassable()).thenReturn((int) i.getArgument(1) >= top);
			return block;
		});
		return world;
	}

	/** Seen at (20, 64, 10) facing north (yaw 180), heading east. */
	private static SpawnBias eastFrom(World world) {
		return new SpawnBias(new Vector(1, 0, 0), new Location(world, 20, 64, 10, 180f, 0f), 10_000L, 60.0);
	}

	@Test
	@DisplayName("open flat ground: 24 blocks east, on the ground, facing east (not the last facing)")
	void openGround_leadsTheFullLengthFacingTheHeading() {
		Location led = eastFrom(world((x, z) -> 64)).lead(24);

		assertEquals(44.0, led.getX(), 1e-9);
		assertEquals(64.0, led.getY(), 1e-9);
		assertEquals(10.0, led.getZ(), 1e-9);
		assertEquals(1.0, led.getDirection().getX(), 1e-6, "the led spot carries the squad-seen heading");
	}

	@Test
	@DisplayName("a wall 10 blocks east stops the lead a block short of it")
	void aWall_stopsTheLeadShort() {
		World world = world((x, z) -> 64);
		when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), eq(FluidCollisionMode.NEVER),
		                          anyBoolean())).thenAnswer(i -> {
			Location eye = i.getArgument(0);
			return new RayTraceResult(new Vector(30, eye.getY(), 10));
		});

		Location led = eastFrom(world).lead(24);

		assertEquals(29.0, led.getX(), 1e-9);
	}

	@Test
	@DisplayName("ground rising three blocks: the lead stands on top, not inside the slope")
	void risingGround_standsOnTop() {
		Location led = eastFrom(world((x, z) -> x >= 40 ? 67 : 64)).lead(24);

		assertEquals(44.0, led.getX(), 1e-9);
		assertEquals(67.0, led.getY(), 1e-9);
	}

	@Test
	@DisplayName("no standing room near his height there: the lead is the last-known spot itself")
	void noStandingRoom_isTheLastKnownSpot() {
		Location led = eastFrom(world((x, z) -> x >= 40 ? 200 : 64)).lead(24);

		assertEquals(20.0, led.getX(), 1e-9);
		assertEquals(64.0, led.getY(), 1e-9);
	}

	@Test
	@DisplayName("a pond east: the only standing room is its bed, under water, so the lead is the last-known spot")
	void aPond_isTheLastKnownSpot() {
		World world = world((x, z) -> x >= 40 ? 60 : 64);
		when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(i -> {
			Block   block = mock(Block.class);
			int     x     = i.getArgument(0);
			int     y     = i.getArgument(1);
			boolean water = x >= 40 && y >= 60 && y < 64;
			when(block.isPassable()).thenReturn(y >= (x >= 40 ? 60 : 64));
			when(block.isLiquid()).thenReturn(water);
			return block;
		});

		Location led = eastFrom(world).lead(24);

		assertEquals(20.0, led.getX(), 1e-9);
		assertEquals(64.0, led.getY(), 1e-9);
	}

	@Test
	@DisplayName("an unloaded chunk on the way: the last-known spot, and no ray trace that would load it")
	void anUnloadedChunkOnTheWay_isTheLastKnownSpotWithoutATrace() {
		World world = world((x, z) -> 64);
		when(world.isChunkLoaded(anyInt(), anyInt())).thenAnswer(i -> (int) i.getArgument(0) != 2);

		Location led = eastFrom(world).lead(40); // 20 -> 60: chunk 1, 2 (blocks 32..47, unloaded), 3

		assertEquals(20.0, led.getX(), 1e-9);
		verify(world, never()).rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(),
		                                      any(FluidCollisionMode.class), anyBoolean());
	}
}
