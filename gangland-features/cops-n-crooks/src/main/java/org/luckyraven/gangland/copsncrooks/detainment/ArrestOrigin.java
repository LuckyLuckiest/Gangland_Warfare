package org.luckyraven.gangland.copsncrooks.detainment;

/**
 * How an arrest came about. Stored on the {@code detainment} row ({@code origin_type}); a row with no origin (written
 * before 0.21.0) reads as {@link #CUFFED}.
 *
 * @since 0.21.0
 */
public enum ArrestOrigin {
	/** A cop cuffed the player (NPC cuff, or {@code /glw cuff}). */
	CUFFED,
	/** The player surrendered and a cop walked up to cuff him. */
	SURRENDERED,
	/** A lethal hit with a cop in cuff range arrested the player instead of downing him. */
	BUSTED_DOWNED,
	/** Reserved for a later release; never written in 0.21.0. */
	DEAD_OR_ALIVE
}
