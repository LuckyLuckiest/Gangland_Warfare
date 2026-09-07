package org.luckyraven.gangland.turf.powerups;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the immutable map of {@link PowerupDefinition} entries loaded from {@code turf_powerups.yml}. Built and swapped
 * by {@code PowerupRegistryLoader} at file-load time; the gangland-impl loader is the only writer, every other consumer
 * (panels, income distributor, capture service) is a reader. Lookups are by lowercase id matching the YAML map key.
 *
 * <p>Replacement is atomic — readers either see the previous catalogue in full or the new catalogue in full,
 * never a half-rebuilt map.
 */
public final class PowerupRegistry {

	private final AtomicReference<Map<String, PowerupDefinition>> byId =
			new AtomicReference<>(Collections.emptyMap());

	public @Nullable PowerupDefinition get(String id) {
		return byId.get().get(id);
	}

	public boolean exists(String id) {
		return byId.get().containsKey(id);
	}

	public Collection<PowerupDefinition> all() {
		return byId.get().values();
	}

	public Set<String> ids() {
		return byId.get().keySet();
	}

	/**
	 * Replaces the catalogue in one shot. Loader callers should pass an unmodifiable view; we re-wrap defensively to
	 * make sure later mutations to the caller's map can't bleed in here.
	 */
	public void replaceAll(Map<String, PowerupDefinition> replacement) {
		byId.set(Collections.unmodifiableMap(new LinkedHashMap<>(replacement)));
	}
}
