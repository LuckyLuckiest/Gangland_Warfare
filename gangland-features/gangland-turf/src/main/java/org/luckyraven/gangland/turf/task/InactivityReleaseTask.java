package org.luckyraven.gangland.turf.task;

import lombok.CustomLog;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.capture.TurfOwnership;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;

import java.util.ArrayList;

/**
 * Daily sweep (driven by {@link GangPresenceTracker}'s 24h tick) that frees every turf whose owning gang has been fully
 * offline for longer than {@code inactivityAutoReleaseDays}. Turfs released here join the unclaimed pool and become
 * immediately capturable. Each release goes through {@link TurfOwnership} and fires one owner-changed event.
 */
@CustomLog
public final class InactivityReleaseTask implements Runnable {

	private final TurfManager        turfs;
	private final GangLookupContract gangs;
	private final TurfOwnership      ownership;
	private final int                inactivityAutoReleaseDays;

	public InactivityReleaseTask(TurfManager turfs,
	                             GangLookupContract gangs,
	                             TurfOwnership ownership,
	                             int inactivityAutoReleaseDays) {
		this.turfs                     = turfs;
		this.gangs                     = gangs;
		this.ownership                 = ownership;
		this.inactivityAutoReleaseDays = inactivityAutoReleaseDays;
	}

	@Override
	public void run() {
		long now         = System.currentTimeMillis();
		long thresholdMs = inactivityAutoReleaseDays * 24L * 60L * 60L * 1000L;
		int  released    = 0;
		for (Turf turf : new ArrayList<>(turfs.getAll())) {
			if (turf.isUnclaimed()) {
				continue;
			}
			Gang owner = gangs.findById(turf.getOwnerGangId());
			if (owner == null) {
				ownership.setOwner(turf, null, TurfOwnerChangedEvent.Cause.ORPHANED);
				released++;
				continue;
			}
			if (owner.getLastMemberOnlineAt() > 0 && now - owner.getLastMemberOnlineAt() > thresholdMs) {
				ownership.setOwner(turf, null, TurfOwnerChangedEvent.Cause.INACTIVITY);
				released++;
			}
		}
		if (released > 0) {
			log.info("Inactivity auto-release swept {} turf(s)", released);
		}
	}
}
