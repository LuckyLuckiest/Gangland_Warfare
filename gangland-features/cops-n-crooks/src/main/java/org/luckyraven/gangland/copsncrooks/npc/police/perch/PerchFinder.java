package org.luckyraven.gangland.copsncrooks.npc.police.perch;

import org.bukkit.Location;
import org.luckyraven.gangland.copsncrooks.npc.police.config.OverwatchSettings;

import java.util.List;

/**
 * Finds roof perches overlooking a last known spot for the Marksman's overwatch (0.20.0): columns on rings around the
 * spot, kept when high enough, loaded and with a clear line to it, nearest first.
 *
 * @since 0.20.0
 */
public final class PerchFinder {

	private PerchFinder() {
	}

	/** Perch standing spots (highest block + 1) for {@code lastKnown}, nearest first; empty without a world. */
	public static List<Location> find(Location lastKnown, OverwatchSettings settings) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
