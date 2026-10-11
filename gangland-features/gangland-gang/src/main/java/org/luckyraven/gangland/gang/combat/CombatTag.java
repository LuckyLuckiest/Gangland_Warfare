package org.luckyraven.gangland.gang.combat;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The short "just hit by another gang" tag and the gang-fight flag. Seam: lane W5-F fills the tag; until then nobody is
 * ever tagged. The fighting flag (used by the fight module) is a plain set.
 */
public class CombatTag {

	private final Set<UUID> fighting = ConcurrentHashMap.newKeySet();

	public boolean isTagged(UUID player) {
		return false;
	}

	public void tag(UUID attacker, UUID victim) {
		// W5-F
	}

	public void setFighting(UUID player, boolean fighting) {
		if (fighting) this.fighting.add(player);
		else this.fighting.remove(player);
	}

	public boolean isFighting(UUID player) {
		return fighting.contains(player);
	}

	public Duration remaining(UUID player) {
		return Duration.ZERO;
	}
}
