package org.luckyraven.gangland.copsncrooks.place;

import lombok.Getter;
import org.luckyraven.gangland.data.region.PlaceRegion;
import org.luckyraven.gangland.data.region.RegionShape;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * An admin-drawn named cuboid ({@code cop_region} row) with tags (district, hideout, restricted, breaker, ...). Corners
 * are normalised so min <= max on every axis; tags are trimmed and lowercase.
 */
@Getter
public final class AdminRegion {

	private final int         id;
	private final String      name;
	private final String      world;
	private final int         minX;
	private final int         minY;
	private final int         minZ;
	private final int         maxX;
	private final int         maxY;
	private final int         maxZ;
	private final Set<String> tags;

	public AdminRegion(int id, String name, String world, int x1, int y1, int z1, int x2, int y2, int z2,
	                   Set<String> tags) {
		this.id    = id;
		this.name  = name;
		this.world = world;
		this.minX  = Math.min(x1, x2);
		this.minY  = Math.min(y1, y2);
		this.minZ  = Math.min(z1, z2);
		this.maxX  = Math.max(x1, x2);
		this.maxY  = Math.max(y1, y2);
		this.maxZ  = Math.max(z1, z2);

		Set<String> clean = new LinkedHashSet<>();
		for (String tag : tags) {
			String t = tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT);
			if (!t.isEmpty()) clean.add(t);
		}
		this.tags = Collections.unmodifiableSet(clean);
	}

	/** The region as every provider consumer sees it: id {@code copsncrooks:<id>}, no owner. */
	public PlaceRegion toPlace() {
		return new PlaceRegion("copsncrooks:" + id, name, world,
		                       RegionShape.Cuboid.of(minX, minY, minZ, maxX, maxY, maxZ), PlaceRegion.NO_OWNER, tags);
	}
}
