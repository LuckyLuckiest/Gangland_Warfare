package org.luckyraven.gangland.data.region;

import org.bukkit.Location;

import java.util.List;

/**
 * SPI a module (or the core) implements to publish places. Main thread.
 *
 * @since api 2.3
 */
public interface RegionProvider {

	/**
	 * Stable source id, also the id prefix: {@code "copsncrooks"}, {@code "turf"}, {@code "waypoint"}.
	 */
	String source();

	/**
	 * Every region of this provider that contains {@code at}; empty, never null.
	 */
	List<PlaceRegion> regionsAt(Location at);
}
