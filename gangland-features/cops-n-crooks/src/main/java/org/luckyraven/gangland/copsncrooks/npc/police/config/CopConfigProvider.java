package org.luckyraven.gangland.copsncrooks.npc.police.config;

import org.luckyraven.gangland.npc.RetreatSettings;
import org.luckyraven.gangland.npc.radio.RadioSettings;
import org.luckyraven.keystone.npc.NpcMeleeProfile;
import org.luckyraven.keystone.npc.NpcNavigationConfig;
import org.luckyraven.keystone.npc.entity.SpawnConfigProvider;

import java.util.Map;
import java.util.Set;

/**
 * Provides all cop-related configuration values.
 * <p>
 * Also implements {@link NpcNavigationConfig} and {@link SpawnConfigProvider} so the shared abstract layers can consume
 * cop config without any additional adapter.
 */
public interface CopConfigProvider extends NpcNavigationConfig, SpawnConfigProvider {

	/**
	 * Cop radio defaults, matching the shipped {@code cops.yml}'s own {@code Cops.Radio} block: this is also what
	 * {@link #getRadioSettings()} returns when a provider doesn't override it.
	 */
	RadioSettings COP_RADIO_DEFAULTS = new RadioSettings(true, 32.0, 64.0, 1500L, 1000L, 25L, 2,
			Map.ofEntries(
					Map.entry("Contact", 8000L), Map.entry("Contact_Lost", 8000L), Map.entry("Engage", 15000L),
					Map.entry("Push", 10000L), Map.entry("Flank_Left", 10000L), Map.entry("Flank_Right", 10000L),
					Map.entry("Reposition", 5000L), Map.entry("Search", 15000L), Map.entry("Route", 15000L),
					Map.entry("Route_High", 15000L), Map.entry("Climb", 15000L), Map.entry("Stand_Down", 10000L),
					Map.entry("No_Route", 15000L), Map.entry("Check_Fire", 8000L), Map.entry("Reloading", 8000L),
					Map.entry("Man_Down", 5000L), Map.entry("Leader_Down", 5000L), Map.entry("Resisting", 10000L),
					Map.entry("Backup", 20000L), Map.entry("Responding", 5000L), Map.entry("Ack", 2000L),
					Map.entry("Fall_Back", 5000L), Map.entry("In_Cover", 10000L)),
			Set.of("Contact", "Man_Down", "Leader_Down", "Backup", "Resisting", "Dispatch_Wanted", "Escalate",
			      "Stand_Down"),
			"BLOCK_NOTE_BLOCK_HAT", 0.4f, 1.8f);

	/**
	 * Returns the cop tier configuration for the given tier level.
	 *
	 * @param tier the tier number
	 *
	 * @return the tier config, or the highest available if tier exceeds max
	 */
	CopTierConfig getTierConfig(int tier);

	/**
	 * Returns the maximum configured tier number.
	 *
	 * @return the max tier
	 */
	int getMaxTier();

	/**
	 * Returns the number of cops to assign per wanted level.
	 *
	 * @return map of wanted level to cop count
	 */
	Map<Integer, Integer> getCopsPerWantedLevel();

	/**
	 * Returns the maximum number of cops allowed per player.
	 *
	 * @return the cap
	 */
	int getMaxCopsPerPlayer();

	/**
	 * Returns the tick rate at which AI should be evaluated.
	 *
	 * @return the AI tick rate
	 */
	int getAiTickRate();

	/**
	 * Returns the tick rate for spawn checks.
	 *
	 * @return the spawn check rate
	 */
	int getSpawnCheckRate();

	/**
	 * Returns the radius within which a cop can attempt to cuff a player.
	 *
	 * @return cuff radius in blocks
	 */
	double getCuffRadius();

	/**
	 * Returns the maximum number of cuff attempts before escalation.
	 *
	 * @return cuff attempt limit
	 */
	int getMaxCuffAttempts();

	/**
	 * Returns the cuffing cooldown in ticks.
	 *
	 * @return the cooldown duration in ticks
	 */
	int getCuffCooldownTicks();

	/**
	 * Returns the alert range in blocks.
	 *
	 * @return the range at which cops become alert
	 */
	double getAlertRange();

	/**
	 * Returns the combat engagement range in blocks.
	 *
	 * @return the combat range
	 */
	double getCombatRange();

	/**
	 * Returns the attack cooldown in ticks.
	 *
	 * @return cooldown ticks
	 */
	int getAttackCooldownTicks();

	/**
	 * Minimum distance from the player to spawn a cop (blocks).
	 */
	double getMinSpawnDistance();

	/**
	 * Maximum distance from the player to spawn a cop (blocks).
	 */
	double getMaxSpawnDistance();

	/**
	 * Minimum distance used during phase-1 (preferred-ring) spawn attempts (blocks).
	 */
	double getPhase1MinDistance();

	/**
	 * Distance the spawn ring radius shrinks per phase-2 iteration (blocks).
	 */
	double getSpawnRadiusShrinkStep();

	/**
	 * Vertical range searched above/below the player's Y level for valid ground (blocks).
	 */
	int getVerticalSearchRange();

	/**
	 * Y-offset applied to the player's Y when searching for spawn ground.
	 */
	int getSpawnYOffset();

	/**
	 * Minimum number of open horizontal sides required at a spawn position.
	 */
	int getMinOpenHorizontalSides();

	/**
	 * Maximum distance within which a registered spawner is preferred over a random position (blocks).
	 */
	double getSpawnerPreferenceRadius();

	/**
	 * Distance within which another player triggers the visibility check during despawn (blocks).
	 */
	double getVisibilityCheckDistance();

	/**
	 * Number of attempts in phase-1 (preferred ring, behind-player) of spawn location selection.
	 */
	int getSpawnPhase1Attempts();

	/**
	 * Number of attempts per shrink step in phase-2 of spawn location selection.
	 */
	int getSpawnPhase2Attempts();

	/**
	 * Ticks between navigation path recalculations.
	 */
	int getNavigationRecalculationTicks();

	/**
	 * AI ticks between movement-progress samples for stuck detection.
	 */
	int getStuckCheckIntervalTicks();

	/**
	 * Consecutive stuck samples before navigation is considered stuck.
	 */
	int getMaxStuckChecks();

	/**
	 * Consecutive stuck samples before navigation is considered permanently hopeless.
	 */
	int getMaxHopelessStuckChecks();

	/**
	 * Distance threshold below which a hopeless cop can still reach the target directly (blocks).
	 */
	double getHopelessCloseThreshold();

	/**
	 * Minimum distance the NPC must travel between samples to count as progress (blocks).
	 */
	double getMinProgressDistance();

	/**
	 * Minimum distance from the target for a ranged cop to hold position (blocks).
	 */
	double getRangedMinDistance();

	/**
	 * Maximum distance from the target for a ranged cop to hold position (blocks).
	 */
	double getRangedMaxDistance();

	/**
	 * Minimum number of AI ticks that must have elapsed from the last path request before a path-loss recovery re-path
	 * is allowed. Guards against requesting a new path before Citizens has finished computing the previous one
	 * (Citizens takes 1–2 ticks to start navigating after {@code setTarget} is called).
	 */
	int getMinRepathAfterLossTicks();

	/**
	 * Horizontal distance (blocks) from the target beyond which a pursuing cop gives up and returns.
	 */
	double getPursuitMaxDistance();

	/**
	 * Maximum AI ticks a cop spends in the PURSUING state before giving up and returning.
	 */
	int getPursuitMaxTicks();

	/**
	 * Maximum AI ticks a cop waits at the station before being force-despawned.
	 */
	int getMaxReturnTicks();

	/**
	 * Distance to a station at which the cop considers itself arrived and attempts despawn (blocks).
	 */
	double getStationArrivalDistance();

	/**
	 * Radius (blocks) the GUARDING cop tries to stay within from the cuffed player awaiting jail transit.
	 */
	default double getGuardRadius() {
		return 5.0;
	}

	/**
	 * Melee tuning shared by every tier's swings (reach, approach, damage falloff). A tier's own approach is then
	 * clamped below its {@code Cuff_Radius} at spawn time — see {@code CopNpcFactory#meleeFor}.
	 */
	default NpcMeleeProfile getMeleeProfile() {
		return NpcMeleeProfile.DEFAULT;
	}

	/** Police-radio delivery tuning ({@code Cops.Radio}). */
	default RadioSettings getRadioSettings() {
		return COP_RADIO_DEFAULTS;
	}

	/** Backup-request tuning ({@code Cops.Backup}). */
	default BackupSettings getBackupSettings() {
		return BackupSettings.DEFAULT;
	}

	/** When a badly hurt cop breaks off to cover ({@code Cops.Retreat}). */
	default RetreatSettings getRetreatSettings() {
		return RetreatSettings.DEFAULT;
	}
}