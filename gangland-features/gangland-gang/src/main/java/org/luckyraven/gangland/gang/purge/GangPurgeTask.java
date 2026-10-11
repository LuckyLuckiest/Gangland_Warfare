package org.luckyraven.gangland.gang.purge;

import java.time.Instant;
import java.util.List;

/** Disbands gangs nobody has played in for a long time. Seam: the body belongs to lane W5-M; off until then. */
public class GangPurgeTask {

	public List<PurgeCandidate> preview(Instant now) {
		return List.of();
	}

	/** @return how many gangs were disbanded. */
	public int run(Instant now) {
		return 0;
	}
}
