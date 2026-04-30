package org.luckyraven.gangland.copsncrooks.npc.turf.defender;

/**
 * Settings for the turf-defender deploy flow. The defender's actual entity stats (model, health, damage, AI tuning,
 * equipment) live in {@code civilians.yml} under the type id {@link #typeId()} — the deployer simply spawns N civilians
 * of that type and points them at attackers. Knobs here are deploy-side only.
 *
 * @param typeId {@code civilians.yml} type id to use for spawned defenders (e.g. {@code turf_defender})
 * @param targetingRadius defenders attack only players within this distance from their own position
 * @param lifespanSeconds safety lifespan — a defender is recalled after this many seconds even if the contest is
 * 		still running. The capture-end event normally cleans them up sooner.
 */
public record TurfDefenderConfig(String typeId, double targetingRadius, int lifespanSeconds) {
}
