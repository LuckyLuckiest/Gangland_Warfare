package org.luckyraven.gangland.turf.powerups;

import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
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

	/** Price of one guard at the Quartermaster, from {@code Garrison.Cost_Per_Guard} (W2-Q, TF-17). */
	private volatile BigDecimal garrisonCost = BigDecimal.valueOf(1000);

	/**
	 * The most a stack of guard boosts can do, from {@code Limits} in {@code turf_powerups.yml}.
	 *
	 * @param maxToughness most health multiplier, at least 1
	 * @param maxWaves most multiplier on wave size and max alive, at least 1
	 * @param minWait shortest wait as a share of the base cooldown, in (0, 1]
	 */
	public record GuardLimits(double maxToughness, double maxWaves, double minWait) {
		public static final GuardLimits DEFAULT = new GuardLimits(3.0, 2.0, 0.25);
	}

	private volatile GuardLimits guardLimits = GuardLimits.DEFAULT;

	public GuardLimits guardLimits() {
		return guardLimits;
	}

	public void setGuardLimits(GuardLimits limits) {
		this.guardLimits = limits;
	}

	public BigDecimal garrisonCost() {
		return garrisonCost;
	}

	public void setGarrisonCost(BigDecimal cost) {
		this.garrisonCost = cost;
	}

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
