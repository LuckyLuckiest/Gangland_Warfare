package org.luckyraven.gangland.gang.stats;

import org.luckyraven.keystone.bean.autowire.DependencyContainer;

import java.util.List;

/** The {@link GangStatContribution} beans, looked up once from the root container (like {@code CommandContributions}). */
public final class GangStatContributions {

	private final List<GangStatContribution> contributions;

	public GangStatContributions(List<GangStatContribution> contributions) {
		this.contributions = List.copyOf(contributions);
	}

	/** Every contribution registered in {@code container}; empty when no module contributed any. */
	public static GangStatContributions from(DependencyContainer container) {
		List<GangStatContribution> found = container.getAllInstances(GangStatContribution.class);
		return new GangStatContributions(found == null ? List.of() : found);
	}

	public List<GangStatContribution> all() {
		return contributions;
	}
}
