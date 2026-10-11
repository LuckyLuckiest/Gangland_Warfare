package org.luckyraven.gangland.turf.npc.config;

/**
 * Routing settings for the per-turf Quartermaster NPC. The actual NPC stats (model, health, equipment, AI tuning,
 * combat behaviour) live in {@code civilians.yml} under the {@link #typeId() type id} below — Quartermasters ARE
 * civilian NPCs, just with right-click panel access layered on top and a hostile target set when the turf is being
 * contested.
 *
 * <p>{@code placementCost} is the price the owning gang pays per {@code /glw turf quartermaster} placement (W7-B,
 * D29=B); staff placement is free.
 */
public record TurfPowerupSettings(String typeId, double targetingRadius, String displayName,
                                  double placementCost) {

	/** Settings with the default Quartermaster name (W2-Q, TF-38). */
	public TurfPowerupSettings(String typeId, double targetingRadius) {
		this(typeId, targetingRadius, "Quartermaster");
	}

	/** Settings with the default placement price (W7-B). */
	public TurfPowerupSettings(String typeId, double targetingRadius, String displayName) {
		this(typeId, targetingRadius, displayName, 5000.0);
	}
}
