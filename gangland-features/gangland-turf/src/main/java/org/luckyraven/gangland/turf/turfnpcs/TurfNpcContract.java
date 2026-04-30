package org.luckyraven.gangland.turf.turfnpcs;

import org.bukkit.Location;

/**
 * Bridge from the turf feature module to the NPC infrastructure that lives in cops-n-crooks. The turf module never
 * imports cops-n-crooks types directly; the contract is implemented in gangland-impl and delegates to the concrete NPC
 * managers (powerup NPC for the on-site Quartermaster, defender deployer for the garrison spawn).
 *
 * <p>Methods are no-ops if the underlying NPC subsystem can't act (e.g. {@code deployDefenders} when the world
 * isn't loaded). Callers don't need to pre-check; they fire and the impl swallows / logs.
 */
public interface TurfNpcContract {

	/**
	 * Spawn N defender NPCs at the given location, set to attack online members of {@code challengerGangId}. Defenders
	 * are civilian NPCs (configured in {@code civilians.yml}) — the system does not create a separate defender entity
	 * type. Defenders despawn after a configured lifespan or when {@link #recallDefenders(int)} fires for the same
	 * turf.
	 */
	void deployDefenders(int turfId, Location spawnLocation, int challengerGangId, int count);

	/**
	 * Despawn all defenders previously deployed for {@code turfId}. Called on capture-complete and capture-failed so a
	 * finished contest cleans up its garrison even if some defenders are still alive.
	 */
	void recallDefenders(int turfId);

	/**
	 * Make the per-turf Quartermaster NPC hostile to {@code challengerGangId}'s online members for the duration of a
	 * contest. Internally sets the underlying civilian's combat target to whichever attacker is closest and forces a
	 * transition into the COMBAT state.
	 */
	void engageQuartermaster(int turfId, int challengerGangId);

	/**
	 * Pacify the per-turf Quartermaster. Clears its combat target and transitions back to IDLE so it stops attacking
	 * once the contest ends.
	 */
	void disengageQuartermaster(int turfId);
}
