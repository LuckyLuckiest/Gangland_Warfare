package org.luckyraven.gangland.gang.combat.region;

import org.bukkit.Location;

import java.util.Optional;

/** A region plugin's say on friendly fire at a spot; {@link #NONE} has no opinion. */
public interface FriendlyFireRegionSource {

	FriendlyFireRegionSource NONE = at -> Optional.empty();

	/** {@code true} allows friendly fire at {@code at}, {@code false} forbids it, empty means no opinion. */
	Optional<Boolean> allowed(Location at);
}
