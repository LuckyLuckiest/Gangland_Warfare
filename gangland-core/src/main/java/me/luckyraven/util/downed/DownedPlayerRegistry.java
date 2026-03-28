package me.luckyraven.util.downed;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks players that are currently in the GTA-style downed state. Other systems should treat a downed player the same
 * as a dead player.
 * <p>
 * Managed by the death listener in gangland-impl; queried by any module that needs to gate behaviour on player death
 * without depending on gangland-impl.
 */
public final class DownedPlayerRegistry {

	private static final Set<UUID> downed = ConcurrentHashMap.newKeySet();

	private DownedPlayerRegistry() { }

	public static void add(UUID uuid) {
		downed.add(uuid);
	}

	public static void remove(UUID uuid) {
		downed.remove(uuid);
	}

	public static boolean isDowned(UUID uuid) {
		return downed.contains(uuid);
	}
}
