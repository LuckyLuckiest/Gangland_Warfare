package org.luckyraven.gangland.civilians.witness;

import java.util.Locale;

/**
 * Why a 911 call did not land. {@link #debugName()} is the {@code reason=} value of the {@code CALL_CANCEL} debug line.
 */
public enum CancelReason {
	HIT,
	SCARED,
	KILLED,
	LOGOUT,
	LOST;

	public String debugName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
