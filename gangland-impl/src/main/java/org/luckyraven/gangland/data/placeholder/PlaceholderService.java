package org.luckyraven.gangland.data.placeholder;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.papi.PlaceholderAPIProvider;
import org.luckyraven.keystone.placeholder.CompositePlaceholderProvider;
import org.luckyraven.keystone.placeholder.PlaceholderProvider;
import org.luckyraven.keystone.util.Placeholder;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregating {@link Placeholder} facade. Lives in the KERNEL phase so FILE-phase consumers can constructor-inject it
 * before any contributor exists. Holds a list of registered {@link Placeholder} resolvers; CONFIG-phase beans (such as
 * {@code GanglandPlaceholder}) self-register into this list from their constructors via
 * {@link #register(Placeholder)}.
 *
 * <p>During the FILE phase the resolver list is empty, so {@link #convert(Player, String)} returns the input text
 * unchanged — exactly matching the legacy null-delegate behavior. Once the CONFIG phase finishes, the registered
 * resolvers are applied in registration order.
 *
 * <p>Resolution runs as a Keystone {@link CompositePlaceholderProvider} chain (1.7.x migration): when the
 * PlaceholderAPI expansion is hooked, PAPI resolves <b>first as one link in the chain</b> — it no longer bypasses the
 * internal registry, so built-in resolvers (money symbol, gang/user tokens) still apply afterwards.
 */
public class PlaceholderService implements Placeholder {

	private final Gangland          gangland;
	private final List<Placeholder> resolvers = new ArrayList<>();

	/**
	 * Created lazily behind the expansion guard so the class — and its PlaceholderAPI imports — never load on a
	 * server without PAPI installed.
	 */
	private PlaceholderProvider papiProvider;

	public PlaceholderService(Gangland gangland) {
		this.gangland = gangland;
	}

	/**
	 * Registers a {@link Placeholder} resolver. Called by CONFIG-phase contributors from their own constructors so the
	 * registry never holds a compile-time reference to any concrete resolver type.
	 */
	public void register(Placeholder resolver) {
		resolvers.add(resolver);
	}

	/**
	 * Resolves placeholders through the composite chain: PlaceholderAPI (when hooked) first, then every registered
	 * resolver in registration order.
	 *
	 * @param player the player object
	 * @param text the string that contains the placeholder(s)
	 *
	 * @return the text with every resolvable placeholder replaced
	 */
	@Override
	public String convert(Player player, String text) {
		List<PlaceholderProvider> chain = new ArrayList<>(resolvers.size() + 1);

		if (gangland.getPlaceholderAPIExpansion() != null) {
			if (papiProvider == null) {
				papiProvider = new PlaceholderAPIProvider();
			}
			chain.add(papiProvider);
		}

		for (Placeholder resolver : resolvers) {
			chain.add((offlinePlayer, raw) -> resolver.convert(player, raw));
		}

		return CompositePlaceholderProvider.of(chain).resolve(player, text);
	}

}
