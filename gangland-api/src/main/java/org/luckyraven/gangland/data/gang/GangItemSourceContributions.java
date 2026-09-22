package org.luckyraven.gangland.data.gang;

import org.bukkit.entity.Player;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

import java.util.List;
import java.util.Map;

/**
 * The {@link GangItemSourceContribution} beans {@code GameplayConfig}'s {@code ItemSourceProvider} can draw on,
 * looked up once from the root container. Modelled on {@code command.extension.CommandContributions}.
 */
public final class GangItemSourceContributions {

	private final List<GangItemSourceContribution> contributions;

	public GangItemSourceContributions(List<GangItemSourceContribution> contributions) {
		this.contributions = List.copyOf(contributions);
	}

	/** Every contribution registered in {@code container}; empty when no module contributed anything. */
	public static GangItemSourceContributions from(DependencyContainer container) {
		List<GangItemSourceContribution> found = container.getAllInstances(GangItemSourceContribution.class);
		return new GangItemSourceContributions(found == null ? List.of() : found);
	}

	public static GangItemSourceContributions none() {
		return new GangItemSourceContributions(List.of());
	}

	/** The first contribution that {@link GangItemSourceContribution#supports(String)} {@code source}, or an empty list. */
	public List<Map<String, String>> entries(Player player, String source) {
		for (GangItemSourceContribution contribution : contributions) {
			if (contribution.supports(source)) return contribution.entries(player, source);
		}
		return List.of();
	}

}
