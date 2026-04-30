package org.luckyraven.gangland.turf.task;

import lombok.CustomLog;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;

/**
 * Daily sweep (driven by {@link GangPresenceTracker}'s 24h tick) that frees every turf whose owning gang has been fully
 * offline for longer than {@code inactivityAutoReleaseDays}. Turfs released here join the unclaimed pool and become
 * immediately capturable.
 */
@CustomLog
public final class InactivityReleaseTask implements Runnable {

	private final TurfManager        turfs;
	private final GangLookupContract gangs;
	private final int                inactivityAutoReleaseDays;

	public InactivityReleaseTask(TurfManager turfs, GangLookupContract gangs, int inactivityAutoReleaseDays) {
		this.turfs                     = turfs;
		this.gangs                     = gangs;
		this.inactivityAutoReleaseDays = inactivityAutoReleaseDays;
	}

	@Override
	public void run() {
		long now         = System.currentTimeMillis();
		long thresholdMs = inactivityAutoReleaseDays * 24L * 60L * 60L * 1000L;
		int  released    = 0;
		for (Turf turf : turfs.getAll()) {
			if (turf.isUnclaimed()) {
				continue;
			}
			Gang owner = gangs.findById(turf.getOwnerGangId());
			if (owner == null) {
				turf.setOwnerGangId(null);
				turfs.persist(turf);
				released++;
				continue;
			}
			if (owner.getLastMemberOnlineAt() > 0 && now - owner.getLastMemberOnlineAt() > thresholdMs) {
				turf.setOwnerGangId(null);
				turfs.persist(turf);
				released++;
			}
		}
		if (released > 0) {
			log.info("Inactivity auto-release swept {} turf(s)", released);
		}
	}
}
