package org.luckyraven.gangland.core.wanted;

/**
 * Why a player's wanted level changed. Carried by every {@link Wanted#setLevel(int, WantedCause)} call and by the
 * three wanted events, so listeners can tell a crime from a decay tick, an arrest or a login restore.
 */
public enum WantedCause {
	/** Heat ledger / kill path (EntityDamageListener.handleWanted). */
	CRIME,
	/** [WANTED] sign INCREASE, REMOVE, CLEAR. */
	SIGN,
	/** /glw wanted add|remove|clear. */
	ADMIN,
	/** Level loaded at login (UserDataLoader). */
	RESTORE,
	/** Repeating_Timer safety-net tick (WantedExecutor). */
	DECAY,
	/** Line-of-sight evasion clock (cops-n-crooks). */
	EVASION,
	/** Handcuff bribe (BribeService). */
	BRIBE,
	/** Jail intake (JailIntakeService). */
	ARREST,
	/** Death / down reset (EntityDamageListener.onPlayerDeathResetWanted). */
	DEATH,
	/** Legacy no-cause calls. */
	UNKNOWN,
	/** Crooked contact: the phone desk ({@code /glw contact}) and bribe-star pickups. */
	CONTACT
}
