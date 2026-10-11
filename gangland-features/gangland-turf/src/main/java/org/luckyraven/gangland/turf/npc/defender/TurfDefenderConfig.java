package org.luckyraven.gangland.turf.npc.defender;

/**
 * Settings for the turf-defender deploy flow. The defender's actual entity stats (model, health, damage, AI tuning,
 * equipment) live in {@code civilians.yml} under the type id {@link #typeId()} — the deployer simply spawns civilians
 * of that type and points them at attackers. Knobs here are deploy-side only.
 *
 * @param typeId {@code civilians.yml} type id to use for spawned defenders (e.g. {@code turf_defender})
 * @param targetingRadius defenders attack only players within this distance from their own position
 * @param lifespanSeconds safety lifespan — a defender is recalled after this many seconds even if the contest is
 * 		still running. The capture-end event normally cleans them up sooner.
 * @param waveSize guards spawned per wave ({@code Garrison.Wave_Size})
 * @param maxAlive most guards standing at once on one turf ({@code Garrison.Max_Alive})
 * @param waveCooldownSeconds seconds after a guard falls before the next top-up
 * @param standDownSeconds seconds survivors stay after the fight before they leave and return to the reserve
 * @param leashMargin blocks past the turf edge a guard may chase before walking back to a post
 * @param composition the roles the guards take in spawn order ({@code Garrison.Composition})
 */
public record TurfDefenderConfig(String typeId, double targetingRadius, int lifespanSeconds, int waveSize,
                                 int maxAlive, int waveCooldownSeconds, int standDownSeconds, int leashMargin,
                                 GarrisonComposition composition) {

	public static final int DEFAULT_WAVE_SIZE = 3;
	public static final int DEFAULT_MAX_ALIVE = 6;
	public static final int DEFAULT_WAVE_COOLDOWN_SECONDS = 20;
	public static final int DEFAULT_STAND_DOWN_SECONDS = 8;
	public static final int DEFAULT_LEASH_MARGIN = 16;

	/** Without a composition: every guard is plain. */
	public TurfDefenderConfig(String typeId, double targetingRadius, int lifespanSeconds, int waveSize, int maxAlive,
	                          int waveCooldownSeconds, int standDownSeconds, int leashMargin) {
		this(typeId, targetingRadius, lifespanSeconds, waveSize, maxAlive, waveCooldownSeconds, standDownSeconds,
		     leashMargin, GarrisonComposition.NONE);
	}

	/** Deploy-side knobs only; the garrison wave knobs take their documented defaults. */
	public TurfDefenderConfig(String typeId, double targetingRadius, int lifespanSeconds) {
		this(typeId, targetingRadius, lifespanSeconds, DEFAULT_WAVE_SIZE, DEFAULT_MAX_ALIVE,
		     DEFAULT_WAVE_COOLDOWN_SECONDS, DEFAULT_STAND_DOWN_SECONDS, DEFAULT_LEASH_MARGIN);
	}
}
