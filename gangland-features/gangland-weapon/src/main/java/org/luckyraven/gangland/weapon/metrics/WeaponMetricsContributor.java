package org.luckyraven.gangland.weapon.metrics;

import org.luckyraven.gangland.metrics.MetricsContributor;
import org.luckyraven.gangland.weapon.configuration.WeaponAddon;

import java.util.Map;
import java.util.function.IntSupplier;

/**
 * Contributes the {@code number_of_weapons} bStats chart, moved out of {@code Gangland.bStats()} when the metrics
 * seam was introduced.
 */
public final class WeaponMetricsContributor implements MetricsContributor {

	private final WeaponAddon weaponAddon;

	public WeaponMetricsContributor(WeaponAddon weaponAddon) {
		this.weaponAddon = weaponAddon;
	}

	@Override
	public Map<String, IntSupplier> singleLineCharts() {
		return Map.of("number_of_weapons", weaponAddon::size);
	}
}
