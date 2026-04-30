package org.luckyraven.gangland.item.money;

/**
 * Source classification for a cash drop. The {@link MoneyDropClassifier} contract maps a dying entity to one of these
 * values; the {@link MoneyAddon} then chooses a variation based on the per-source rules in {@code money.yml}.
 */
public enum MoneyDropContext {

	/**
	 * A real {@code Player} died.
	 */
	PLAYER,

	/**
	 * A cop NPC died.
	 */
	COP,

	/**
	 * A civilian NPC died.
	 */
	CIVILIAN,

	/**
	 * Any other living entity (vanilla mob, etc.).
	 */
	MOB

}
