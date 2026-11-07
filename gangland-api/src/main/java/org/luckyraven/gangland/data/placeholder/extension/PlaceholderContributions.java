package org.luckyraven.gangland.data.placeholder.extension;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

import java.util.List;
import java.util.function.Function;

/**
 * The {@link PlaceholderContribution} beans {@code GanglandPlaceholder} can draw on, looked up once from the root
 * container. Modelled on {@code command.extension.CommandContributions}.
 */
public final class PlaceholderContributions {

	private final List<PlaceholderContribution> contributions;

	public PlaceholderContributions(List<PlaceholderContribution> contributions) {
		this.contributions = List.copyOf(contributions);
	}

	/** Every contribution registered in {@code container}; empty when no module contributed anything. */
	public static PlaceholderContributions from(DependencyContainer container) {
		List<PlaceholderContribution> found = container.getAllInstances(PlaceholderContribution.class);
		return new PlaceholderContributions(found == null ? List.of() : found);
	}

	/** The first non-null playerless answer from any contribution, or {@code null} if none owns it. */
	@Nullable
	public String resolveSetting(String parameter) {
		return firstNonNull(contribution -> contribution.resolveSetting(parameter));
	}

	/** The first non-null playerless, non-setting answer ({@code resolveGlobal}), or {@code null}. Since api 2.6. */
	@Nullable
	public String resolveGlobal(String token) {
		return firstNonNull(contribution -> contribution.resolveGlobal(token));
	}

	public static PlaceholderContributions none() {
		return new PlaceholderContributions(List.of());
	}

	/** The first non-null answer from any contribution, in registration order, or {@code null} if none owns it. */
	@Nullable
	public String resolve(OfflinePlayer player, String parameter) {
		return firstNonNull(contribution -> contribution.resolve(player, parameter));
	}

	@Nullable
	private String firstNonNull(Function<PlaceholderContribution, String> ask) {
		for (PlaceholderContribution contribution : contributions) {
			String value = ask.apply(contribution);
			if (value != null) return value;
		}
		return null;
	}
}
