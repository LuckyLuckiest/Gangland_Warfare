package org.luckyraven.gangland.turf.npc.defender;

/**
 * Settings for the turf-defender deploy flow. The defender's actual entity stats (model, health, damage, AI tuning,
 * equipment) live in {@code civilians.yml} under the type id {@link #typeId()} — the deployer simply spawns civilians
 * of that type and points them at attackers. Knobs here are deploy-side only.
 *
 * @param typeId {@code civilians.yml} type id to use for spawned defenders (e.g. {@code turf_defender})
 * @param targetingRadius defenders attack only players within this distance from their own position
 * @param waveSize guards spawned per wave ({@code Garrison.Wave_Size})
 * @param maxAlive most guards standing during a contest, and the ceiling for {@code standingCap}
 * 		({@code Garrison.Max_Alive})
 * @param waveCooldownSeconds seconds after a guard falls before the next top-up
 * @param standDownSeconds seconds after a fight before guards past {@code standingCap} leave
 * @param leashMargin blocks past the turf edge a guard may chase before walking back to a post
 * @param composition the roles the guards take in spawn order ({@code Garrison.Composition})
 * @param bountyShare share of a guard's price paid to a rival who kills it ({@code Garrison.Bounty_Share}, 0 = off)
 * @param recycleSeconds unreachable and unseen for this long and a guard reappears at another post
 * 		({@code Garrison.Recycle_Seconds}, 0 = off)
 * @param holdRadius blocks around its post a guard keeps to under the Hold order ({@code Garrison.Hold_Radius})
 * @param standingCap guards standing outside a contest, one per post; 0 means reserve-only
 * 		({@code Garrison.Standing_Cap}, not scaled by any buff)
 */
public record TurfDefenderConfig(String typeId, double targetingRadius, int waveSize, int maxAlive,
                                 int waveCooldownSeconds, int standDownSeconds, int leashMargin,
                                 GarrisonComposition composition, double bountyShare,
                                 int recycleSeconds, double holdRadius, int standingCap) {

	public static final int DEFAULT_WAVE_SIZE = 3;
	public static final int DEFAULT_MAX_ALIVE = 6;
	public static final int DEFAULT_WAVE_COOLDOWN_SECONDS = 20;
	public static final int DEFAULT_STAND_DOWN_SECONDS = 8;
	public static final int DEFAULT_LEASH_MARGIN = 16;
	public static final double DEFAULT_BOUNTY_SHARE = 0.5;
	public static final int DEFAULT_RECYCLE_SECONDS = 12;
	public static final double DEFAULT_HOLD_RADIUS = 6.0;
	public static final int DEFAULT_STANDING_CAP = 5;

	/** Without a hold radius or a standing cap: the shipped defaults. */
	public TurfDefenderConfig(String typeId, double targetingRadius, int waveSize, int maxAlive,
	                          int waveCooldownSeconds, int standDownSeconds, int leashMargin,
	                          GarrisonComposition composition, double bountyShare, int recycleSeconds) {
		this(typeId, targetingRadius, waveSize, maxAlive, waveCooldownSeconds, standDownSeconds, leashMargin,
		     composition, bountyShare, recycleSeconds, DEFAULT_HOLD_RADIUS, DEFAULT_STANDING_CAP);
	}

	/** Without a bounty share or recycle time: the shipped defaults. */
	public TurfDefenderConfig(String typeId, double targetingRadius, int waveSize, int maxAlive,
	                          int waveCooldownSeconds, int standDownSeconds, int leashMargin,
	                          GarrisonComposition composition) {
		this(typeId, targetingRadius, waveSize, maxAlive, waveCooldownSeconds, standDownSeconds, leashMargin,
		     composition, DEFAULT_BOUNTY_SHARE, DEFAULT_RECYCLE_SECONDS);
	}

	/** Without a composition: every guard is plain. */
	public TurfDefenderConfig(String typeId, double targetingRadius, int waveSize, int maxAlive,
	                          int waveCooldownSeconds, int standDownSeconds, int leashMargin) {
		this(typeId, targetingRadius, waveSize, maxAlive, waveCooldownSeconds, standDownSeconds, leashMargin,
		     GarrisonComposition.NONE);
	}

	/** Deploy-side knobs only; the garrison wave knobs take their documented defaults. */
	public TurfDefenderConfig(String typeId, double targetingRadius) {
		this(typeId, targetingRadius, DEFAULT_WAVE_SIZE, DEFAULT_MAX_ALIVE, DEFAULT_WAVE_COOLDOWN_SECONDS,
		     DEFAULT_STAND_DOWN_SECONDS, DEFAULT_LEASH_MARGIN);
	}

	/** A copy with the buffed wave numbers; everything else, {@code standingCap} included, stays. */
	public TurfDefenderConfig withWaves(int waveSize, int maxAlive, int waveCooldownSeconds) {
		return new TurfDefenderConfig(typeId, targetingRadius, waveSize, maxAlive, waveCooldownSeconds,
		                              standDownSeconds, leashMargin, composition, bountyShare, recycleSeconds,
		                              holdRadius, standingCap);
	}
}
