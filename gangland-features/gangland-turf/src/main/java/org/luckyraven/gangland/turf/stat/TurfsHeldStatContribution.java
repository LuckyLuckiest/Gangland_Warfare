package org.luckyraven.gangland.turf.stat;

import org.luckyraven.gangland.gang.stats.GangStatContribution;
import org.luckyraven.gangland.turf.manager.TurfManager;

/** The {@code turfs} gang ranking stat: how many turfs the gang owns right now. */
public final class TurfsHeldStatContribution implements GangStatContribution {

	private final TurfManager turfs;

	public TurfsHeldStatContribution(TurfManager turfs) {
		this.turfs = turfs;
	}

	@Override
	public String key() {
		return "turfs";
	}

	@Override
	public long value(int gangId) {
		return turfs.countOwnedBy(gangId);
	}
}
