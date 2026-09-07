package org.luckyraven.gangland.turf.turfnpcs;

import org.bukkit.Location;

/**
 * Core holder for {@link TurfNpcContract}. Always present as a core bean so {@code GarrisonDeployListener} always
 * constructs; every method is a no-op until the cops-n-crooks module installs its {@code TurfNpcContractImpl}
 * delegate from {@code CopsNCrooksModuleConfig}'s {@code @PostConstruct} hook (see
 * {@code documentation/module-loader.md}, "Core seams"). Never register a second bean under
 * {@link TurfNpcContract} — install a delegate into this holder instead.
 */
public final class TurfNpcContracts implements TurfNpcContract {

	private volatile TurfNpcContract delegate;

	public void install(TurfNpcContract delegate) {
		this.delegate = delegate;
	}

	@Override
	public void deployDefenders(int turfId, Location spawnLocation, int challengerGangId, int count) {
		TurfNpcContract current = this.delegate;
		if (current != null) {
			current.deployDefenders(turfId, spawnLocation, challengerGangId, count);
		}
	}

	@Override
	public void recallDefenders(int turfId) {
		TurfNpcContract current = this.delegate;
		if (current != null) {
			current.recallDefenders(turfId);
		}
	}

	@Override
	public void engageQuartermaster(int turfId, int challengerGangId) {
		TurfNpcContract current = this.delegate;
		if (current != null) {
			current.engageQuartermaster(turfId, challengerGangId);
		}
	}

	@Override
	public void disengageQuartermaster(int turfId) {
		TurfNpcContract current = this.delegate;
		if (current != null) {
			current.disengageQuartermaster(turfId);
		}
	}
}
