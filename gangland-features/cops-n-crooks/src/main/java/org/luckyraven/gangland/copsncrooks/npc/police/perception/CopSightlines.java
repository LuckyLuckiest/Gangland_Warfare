package org.luckyraven.gangland.copsncrooks.npc.police.perception;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Line-of-sight geometry shared by the cop lanes; {@code CopManager.inView} moved here unchanged. */
public final class CopSightlines {

	private CopSightlines() {
	}

	/** {@code body} is in front of {@code player}'s eyes (within 60 degrees) with a clear line within {@code maxDistance}. */
	public static boolean inView(Player player, LivingEntity body, double maxDistance) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
