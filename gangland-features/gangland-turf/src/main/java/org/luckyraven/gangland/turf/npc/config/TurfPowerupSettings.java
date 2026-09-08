package org.luckyraven.gangland.copsncrooks.npc.turf.config;

/**
 * Routing settings for the per-turf Quartermaster NPC. The actual NPC stats (model, health, equipment, AI tuning,
 * combat behaviour) live in {@code civilians.yml} under the {@link #typeId() type id} below — Quartermasters ARE
 * civilian NPCs, just with right-click panel access layered on top and a hostile target set when the turf is being
 * contested.
 */
public record TurfPowerupSettings(String typeId) {
}
