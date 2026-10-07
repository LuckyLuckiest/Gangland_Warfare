package org.luckyraven.gangland.data.region;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * One named place. {@code id} is globally unique: {@code "<source>:<local id>"}, e.g. {@code "copsncrooks:12"},
 * {@code "turf:3"}, {@code "waypoint:7"}.
 *
 * @since api 2.3
 */
public record PlaceRegion(String id, String name, String world, RegionShape shape, int ownerGangId,
                          Set<String> tags) {

	public static final String TAG_DISTRICT   = "district";
	public static final String TAG_HIDEOUT    = "hideout";
	public static final String TAG_RESTRICTED = "restricted";
	public static final String TAG_TURF       = "turf";
	public static final String TAG_BREAKER    = "breaker";

	/**
	 * {@link #ownerGangId} of a place no gang owns.
	 */
	public static final int NO_OWNER = -1;

	public PlaceRegion {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(world, "world");
		Objects.requireNonNull(shape, "shape");
		name = name == null ? "" : name;
		tags = tags == null ? Set.of() : Set.copyOf(tags);
	}

	public boolean contains(@Nullable Location at) {
		if (at == null) return false;

		World atWorld = at.getWorld();
		if (atWorld == null || !world.equals(atWorld.getName())) return false;

		return shape.contains(at.getX(), at.getY(), at.getZ());
	}

	public boolean hasTag(String tag) {
		return tags.contains(tag);
	}
}
