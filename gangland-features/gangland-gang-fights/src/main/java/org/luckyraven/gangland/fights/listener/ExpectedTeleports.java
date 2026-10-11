package org.luckyraven.gangland.fights.listener;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * The teleports the fight itself performs (spawn-in, send-home, put-back). {@link FightContainmentListener} lets
 * these through because a {@code PlayerTeleportEvent} with cause PLUGIN cannot tell the fight's own teleport from a
 * waypoint's. The teleport event fires synchronously inside {@code teleport(...)}, so the flag only lives for the
 * duration of that call.
 */
public final class ExpectedTeleports {

	private static final Set<UUID> EXPECTED = ConcurrentHashMap.newKeySet();

	private ExpectedTeleports() {
	}

	/** Runs {@code teleport} with the player marked as expected to teleport; returns what it returns. */
	public static boolean run(UUID player, BooleanSupplier teleport) {
		boolean added = EXPECTED.add(player);
		try {
			return teleport.getAsBoolean();
		} finally {
			if (added) EXPECTED.remove(player);
		}
	}

	public static boolean expected(UUID player) {
		return EXPECTED.contains(player);
	}
}
