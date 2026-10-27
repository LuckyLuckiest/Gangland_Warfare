package org.luckyraven.gangland.copsncrooks.npc.police.perception;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/** Line-of-sight geometry shared by the cop lanes; {@code CopManager.inView} moved here unchanged. */
public final class CopSightlines {

	/** A view within this many blocks always protects a cop (the stranded release, a recycle past its overdue mark). */
	public static final double WATCH_MIN_BLOCKS = 24.0;

	private CopSightlines() {
	}

	/**
	 * Owner ruling R3: a real player, the suspect or a bystander, has {@code body} {@link #inView} within
	 * {@code max(24, visibilityCheckDistance)}, so a cop leaving the map there would vanish before his eyes. Shared by
	 * the spawn run's stranded release and the walk home's ({@code ReturningBehavior}).
	 */
	public static boolean watched(LivingEntity body, double visibilityCheckDistance) {
		return inViewOfPlayers(body, null, Math.max(WATCH_MIN_BLOCKS, visibilityCheckDistance));
	}

	/**
	 * A real player other than {@code except} has {@code body} {@link #inView} within {@code maxDistance}. Citizens
	 * NPCs (cops are PLAYER-type, so the cop itself) are in {@code world.getPlayers()} and are skipped.
	 */
	public static boolean inViewOfPlayers(LivingEntity body, @Nullable Player except, double maxDistance) {
		Location at = body.getLocation();
		if (at.getWorld() == null) return false;
		for (Player other : at.getWorld().getPlayers())
			if (!other.equals(except) && !other.hasMetadata("NPC") && inView(other, body, maxDistance)) return true;
		return false;
	}

	/** {@code body} is in front of {@code player}'s eyes (within 60 degrees) with a clear line within {@code maxDistance}. */
	public static boolean inView(Player player, LivingEntity body, double maxDistance) {
		Location eye    = player.getEyeLocation();
		Vector   toward = body.getLocation().toVector().add(new Vector(0, 1, 0)).subtract(eye.toVector());
		if (toward.lengthSquared() > maxDistance * maxDistance) return false;
		return eye.getDirection().angle(toward) < Math.PI / 3 && player.hasLineOfSight(body);
	}
}
