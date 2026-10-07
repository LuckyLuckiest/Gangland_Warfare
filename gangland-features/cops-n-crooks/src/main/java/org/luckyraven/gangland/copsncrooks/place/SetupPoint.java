package org.luckyraven.gangland.copsncrooks.place;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

/** An admin-placed point of a kind ({@link #PICKUP} or {@link #BREAKER_TRIGGER}); the world is kept by name. */
@Getter
public final class SetupPoint {

	public static final String PICKUP          = "pickup";
	public static final String BREAKER_TRIGGER = "breaker_trigger";

	private final int    id;
	private final String kind;
	private final String name;
	private final String world;
	private final double x;
	private final double y;
	private final double z;

	public SetupPoint(int id, String kind, String name, String world, double x, double y, double z) {
		this.id    = id;
		this.kind  = kind;
		this.name  = name;
		this.world = world;
		this.x     = x;
		this.y     = y;
		this.z     = z;
	}

	/** The point, or {@code null} while its world is not loaded. */
	public @Nullable Location getLocation() {
		World loaded = Bukkit.getWorld(world);
		return loaded == null ? null : new Location(loaded, x, y, z);
	}
}
