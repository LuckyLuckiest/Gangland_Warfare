package org.luckyraven.gangland.copsncrooks.npc.police.state;

/**
 * Lists all possible behavior states for a cop NPC.
 */
public enum CopState {
	IDLE,
	PURSUING,
	CUFFING,
	GUARDING,
	COMBAT,
	RETURNING,
	/** Holding a perimeter post ({@code Cops.Perimeter}, 0.16.0): watching for the suspect, never cuffing. */
	POSTED,
	/** Walking a beat (0.20.0 lane patrol): scanning for crime, never cuffing. */
	PATROL,
	/** Warning a suspect before acting (0.20.0 lane patrol). */
	WARNING,
	/** Watching from a vantage point (0.20.0 lane overwatch); counts as engaged for the hand-off. */
	OVERWATCH
}