package org.luckyraven.gangland.copsncrooks.npc.police.perception;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Line-of-sight geometry shared by the cop lanes; {@code CopManager.inView} moved here unchanged. */
public final class CopSightlines {

	private CopSightlines() {
	}

	/** {@code body} is in front of {@code player}'s eyes (within 60 degrees) with a clear line within {@code maxDistance}. */
	public static boolean inView(Player player, LivingEntity body, double maxDistance) {
		Location eye    = player.getEyeLocation();
		Vector   toward = body.getLocation().toVector().add(new Vector(0, 1, 0)).subtract(eye.toVector());
		if (toward.lengthSquared() > maxDistance * maxDistance) return false;
		return eye.getDirection().angle(toward) < Math.PI / 3 && player.hasLineOfSight(body);
	}
}
