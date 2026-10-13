package org.luckyraven.gangland.gang.chat;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Which staff members read gang and ally chat (in memory, forgotten on restart). */
public class ChatSpyService {

	private final Set<UUID> spies = ConcurrentHashMap.newKeySet();

	/** Switches spying for {@code staff}; returns the new state. */
	public boolean toggle(UUID staff) {
		if (spies.add(staff)) return true;

		spies.remove(staff);
		return false;
	}

	public Set<UUID> spies() {
		return Collections.unmodifiableSet(spies);
	}
}
