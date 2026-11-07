package org.luckyraven.gangland.sign.extension;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.sign.type.Sign;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The {@link SignTypeContribution} and {@link SignViewProvider} beans a sign catalogue can draw on, looked up once
 * from the root container. Modelled on {@code command.extension.CommandContributions} — the core cannot name a
 * module type, so this is a plain snapshot of whatever beans of both interfaces exist when it is resolved.
 */
public final class SignContributions {

	private final List<SignTypeContribution> typeContributions;
	private final List<SignViewProvider>     viewProviders;

	public SignContributions(List<SignTypeContribution> typeContributions, List<SignViewProvider> viewProviders) {
		this.typeContributions = List.copyOf(typeContributions);
		this.viewProviders     = List.copyOf(viewProviders);
	}

	/** Every contribution registered in {@code container}; empty when no module contributed anything. */
	public static SignContributions from(DependencyContainer container) {
		List<SignTypeContribution> types = container.getAllInstances(SignTypeContribution.class);
		List<SignViewProvider>     views = container.getAllInstances(SignViewProvider.class);
		return new SignContributions(types == null ? List.of() : types, views == null ? List.of() : views);
	}

	public static SignContributions none() {
		return new SignContributions(List.of(), List.of());
	}

	/** Build the sign types every contribution provides, in registration order. */
	public List<Sign> createSigns(String signPrefix) {
		return typeContributions.stream()
		                        .flatMap(contribution -> contribution.signs(signPrefix).stream())
		                        .collect(Collectors.toCollection(ArrayList::new));
	}

	/** Opens {@code content} with the first provider that claims it; {@code false} when none do. */
	public boolean openView(Player player, String content) {
		return viewProviders.stream().anyMatch(provider -> provider.open(player, content));
	}
}
