package org.luckyraven.gangland.fights.arena;

import org.luckyraven.gangland.data.region.RegionShape;

/** The block box of an arena. Edges are inclusive, like {@link RegionShape.Cuboid}. */
public record ArenaBox(String world, RegionShape.Cuboid cuboid) {

	public boolean contains(String world, double x, double y, double z) {
		return this.world.equals(world) && cuboid.contains(x, y, z);
	}

	/** Number of blocks in the box. */
	public int volume() {
		return (cuboid.maxX() - cuboid.minX() + 1) * (cuboid.maxY() - cuboid.minY() + 1)
		       * (cuboid.maxZ() - cuboid.minZ() + 1);
	}
}
