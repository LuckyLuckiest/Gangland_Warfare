package org.luckyraven.gangland.data.region;

import lombok.CustomLog;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Core holder bean; inert (empty answers) until a provider registers.
 *
 * @since api 2.3
 */
@CustomLog
public final class PlaceNames {

	private final List<RegionProvider> providers = new CopyOnWriteArrayList<>();

	public PlaceNames() {
	}

	/**
	 * Adds a provider; a second provider with the same {@link RegionProvider#source()} replaces the first.
	 */
	public void register(RegionProvider provider) {
		unregister(provider.source());
		providers.add(provider);
	}

	public void unregister(String source) {
		providers.removeIf(provider -> provider.source().equals(source));
	}

	/**
	 * Every provider's regions at {@code at}, smallest footprint first (ties: registration order). A provider that
	 * throws a RuntimeException is skipped for that call. Empty when {@code at} or its world is null.
	 */
	public List<PlaceRegion> regionsAt(@Nullable Location at) {
		if (at == null || at.getWorld() == null) return List.of();

		List<PlaceRegion> found = new ArrayList<>();
		for (RegionProvider provider : providers) {
			try {
				found.addAll(provider.regionsAt(at));
			} catch (RuntimeException | LinkageError exception) {
				// LinkageError: a module provider whose soft-dependency class is gone is skipped like a failing one
				log.warn("Region provider '{}' failed: {}", provider.source(), exception.toString());
			}
		}

		// List.sort is stable, so equal footprints keep registration order
		found.sort(Comparator.comparingDouble(region -> region.shape().footprint()));
		return found;
	}

	/**
	 * First of {@link #regionsAt} with a non-blank name.
	 */
	public Optional<PlaceRegion> placeAt(@Nullable Location at) {
		return regionsAt(at).stream().filter(region -> !region.name().isBlank()).findFirst();
	}

	/**
	 * The name of {@link #placeAt}.
	 */
	public Optional<String> locate(@Nullable Location at) {
		return placeAt(at).map(PlaceRegion::name);
	}

	/**
	 * Smallest region at {@code at} carrying {@code tag}.
	 */
	public Optional<PlaceRegion> withTag(@Nullable Location at, String tag) {
		return regionsAt(at).stream().filter(region -> region.hasTag(tag)).findFirst();
	}
}
