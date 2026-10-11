package org.luckyraven.gangland.gang.stats;

import java.util.UUID;

/** Gang kills, deaths, assists and the ranking snapshot. Seam: the body belongs to lane W5-S. */
public class GangStatsService {

	/** The ranking at one moment. W5-S may add components; only W5-S reads them. */
	public record Snapshot(long takenAtMillis) { }

	public Snapshot snapshot() {
		return new Snapshot(0L);
	}

	public void resetPlayer(UUID player) {
		// W5-S
	}
}
