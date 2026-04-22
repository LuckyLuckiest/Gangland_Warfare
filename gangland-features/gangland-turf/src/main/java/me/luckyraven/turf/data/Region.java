package me.luckyraven.turf.data;

import org.bukkit.Location;

/**
 * Geometric shape used by a turf to test whether a location is inside it. Kept as an interface so later phases can add
 * sphere / polygon shapes without changing Turf or the detection task.
 */
public interface Region {

	boolean contains(Location location);

	String getWorld();
}
