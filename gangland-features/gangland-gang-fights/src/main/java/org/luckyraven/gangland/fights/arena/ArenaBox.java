package org.luckyraven.gangland.fights.arena;

import org.luckyraven.gangland.data.region.RegionShape;

/** The block box of an arena. Edges are inclusive, like {@link RegionShape.Cuboid}. */
public record ArenaBox(String world, RegionShape.Cuboid cuboid) {

	public static ArenaBox of(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
		return new ArenaBox(world, RegionShape.Cuboid.of(x1, y1, z1, x2, y2, z2));
	}

	public boolean contains(String world, double x, double y, double z) {
		return this.world.equals(world) && cuboid.contains(x, y, z);
	}

	public boolean contains(ArenaPoint point) {
		return contains(point.world(), point.x(), point.y(), point.z());
	}

	/** Number of blocks in the box. */
	public int volume() {
		return (cuboid.maxX() - cuboid.minX() + 1) * (cuboid.maxY() - cuboid.minY() + 1)
		       * (cuboid.maxZ() - cuboid.minZ() + 1);
	}

	/** true when both boxes are in one world and share at least one block. */
	public boolean overlaps(ArenaBox other) {
		return false;
	}
}
