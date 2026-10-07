package org.luckyraven.gangland.data.region;

/**
 * A region's 3D shape. Block coordinates for cuboids (inclusive on both ends, like turf's CuboidRegion).
 *
 * @since api 2.3
 */
public sealed interface RegionShape permits RegionShape.Cuboid, RegionShape.Sphere {

	boolean contains(double x, double y, double z);

	/**
	 * Horizontal area in square blocks; the "smaller wins" key of {@link PlaceNames}.
	 */
	double footprint();

	record Cuboid(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) implements RegionShape {

		/**
		 * Normalises two corners (min/max per axis).
		 */
		public static Cuboid of(int x1, int y1, int z1, int x2, int y2, int z2) {
			return new Cuboid(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2),
			                  Math.max(z1, z2));
		}

		/**
		 * Unbounded in Y: a turf column.
		 */
		public static Cuboid column(int x1, int z1, int x2, int z2) {
			return of(x1, Integer.MIN_VALUE, z1, x2, Integer.MAX_VALUE, z2);
		}

		@Override
		public boolean contains(double x, double y, double z) {
			int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);

			return bx >= minX && bx <= maxX && by >= minY && by <= maxY && bz >= minZ && bz <= maxZ;
		}

		@Override
		public double footprint() {
			return (maxX - (double) minX + 1.0) * (maxZ - (double) minZ + 1.0);
		}
	}

	record Sphere(double x, double y, double z, double radius) implements RegionShape {

		@Override
		public boolean contains(double px, double py, double pz) {
			double dx = px - x, dy = py - y, dz = pz - z;

			return dx * dx + dy * dy + dz * dz <= radius * radius;
		}

		@Override
		public double footprint() {
			return Math.PI * radius * radius;
		}
	}
}
