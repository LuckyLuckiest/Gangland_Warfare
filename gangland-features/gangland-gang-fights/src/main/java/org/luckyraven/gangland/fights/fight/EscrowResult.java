package org.luckyraven.gangland.fights.fight;

import org.jetbrains.annotations.Nullable;

/** Outcome of taking both gangs' stakes. {@code failureKey} is a message key in {@code fight_messages.yml}. */
public record EscrowResult(boolean ok, @Nullable String failureKey) {

	public static EscrowResult success() {
		return new EscrowResult(true, null);
	}

	public static EscrowResult fail(String failureKey) {
		return new EscrowResult(false, failureKey);
	}
}
