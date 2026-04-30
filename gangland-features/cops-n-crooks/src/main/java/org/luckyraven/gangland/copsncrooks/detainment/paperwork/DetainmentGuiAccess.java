package org.luckyraven.gangland.copsncrooks.detainment.paperwork;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Allowlist of players currently authorised to open a detainment GUI (handcuff bribe / paperwork).
 * {@code DetainmentListener} consults this set before cancelling {@code InventoryOpenEvent} for restrained players so
 * our own menus are allowed through while still blocking every other inventory. Views call {@link #authorize} before
 * opening and {@link #revoke} once the GUI is dismissed.
 */
public final class DetainmentGuiAccess {

	private static final Set<UUID> AUTHORISED = ConcurrentHashMap.newKeySet();

	private DetainmentGuiAccess() { }

	public static void authorize(UUID playerId) {
		if (playerId != null) AUTHORISED.add(playerId);
	}

	public static void revoke(UUID playerId) {
		if (playerId != null) AUTHORISED.remove(playerId);
	}

	public static boolean isAuthorized(UUID playerId) {
		return playerId != null && AUTHORISED.contains(playerId);
	}
}
