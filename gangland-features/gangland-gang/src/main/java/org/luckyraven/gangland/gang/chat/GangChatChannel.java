package org.luckyraven.gangland.gang.chat;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who has gang chat switched on (in memory; forgotten on quit). W2-P2 adds the delivery; this much is the final shape
 * the gang command constructor takes.
 */
public final class GangChatChannel {

	private final Set<UUID> on = ConcurrentHashMap.newKeySet();

	/** Switches gang chat for {@code player}; returns the new state. */
	public boolean toggle(UUID player) {
		if (on.add(player)) {
			return true;
		}
		on.remove(player);
		return false;
	}

	public boolean isOn(UUID player) {
		return on.contains(player);
	}

	public void forget(UUID player) {
		on.remove(player);
	}

}
