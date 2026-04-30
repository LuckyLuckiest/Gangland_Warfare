package org.luckyraven.gangland.gang.bounty;

/**
 * Provides the data a {@link BountyExecutor} needs from its owning entity.
 * <p>
 * Implementations live in {@code gangland-impl} (e.g. {@code User}) so that {@code cops-n-crooks} stays decoupled from
 * the main plugin's account layer.
 */
public interface BountyContext {

	/**
	 * Returns the bounty for the entity.
	 */
	Bounty getBounty();

	/**
	 * Returns the entity's current level value, used for level-scaled bounty calculations.
	 */
	int getUserLevel();
}
