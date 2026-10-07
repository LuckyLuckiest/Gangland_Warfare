package org.luckyraven.gangland.copsncrooks.npc.police.dispatch;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.station.Station;

/**
 * A dispatched cop on its way: it spawns once the radio clock reaches {@code arriveAt}.
 *
 * @param role     the squad role it fills ({@code null} with roles off).
 * @param tier     the tier it spawns at (its composition slot's tier, else the star's).
 * @param arriveAt radio-clock ms it is due: breather or rejoin hold, plus the station ETA.
 * @param station  the station it left from, {@code null} for a ring unit (no station in the world).
 * @param bias     the hand-off bias active WHEN THE UNIT WAS ENQUEUED (null when none); it travels with the unit, so a
 *                 unit that arrives after the 10 s bias window still spawns ahead and seeded.
 * @since 0.16.0
 */
public record PendingUnit(@Nullable CopRole role, int tier, long arriveAt, @Nullable Station station,
                          @Nullable SpawnBias bias) {

	/** It left a station; the 0.20 mounted-unit flag reads this. */
	public boolean fromStation() {
		return station != null;
	}
}
