package org.luckyraven.gangland.metrics;

import java.util.Map;
import java.util.function.IntSupplier;

/**
 * Extra bStats charts a runtime module contributes. The core cannot name a module type, so a module registers a bean
 * implementing this contract; {@code Gangland.bStats()} pulls every implementation out of the container and wraps
 * each entry in a {@code SingleLineChart}. bStats types never cross this boundary — they are shaded and relocated
 * into the core jar, so a module jar must not reference them.
 */
public interface MetricsContributor {

	/** Chart id → value supplier. Ids must be unique across the plugin. */
	Map<String, IntSupplier> singleLineCharts();
}
