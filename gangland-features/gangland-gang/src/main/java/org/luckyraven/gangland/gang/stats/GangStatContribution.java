package org.luckyraven.gangland.gang.stats;

/** One extra number a module contributes to the gang ranking (turfs held, fight wins, ...). */
public interface GangStatContribution {

	/** The ranking stat name, lower case, e.g. {@code turfs}. */
	String key();

	long value(int gangId);
}
