package org.luckyraven.gangland.data.teleportation;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.user.User;

import java.util.Collection;
import java.util.List;

/**
 * Decides which waypoints a player may see, select and teleport to.
 *
 * <p>The rule itself is not new — {@code TeleportCommand}, {@code WaypointSelectCommand} and the waypoint
 * tab-completers already built their suggestion lists from it. What was missing is that the <em>action bodies</em>
 * never applied it: typing {@code /glw waypoint select 7} or {@code /glw teleport <name>} for a waypoint that was
 * never suggested worked anyway, and {@code /glw waypoint list} printed every waypoint in the server with a
 * click-to-run teleport component. This class is the single place the rule lives so a fourth caller cannot forget it.
 *
 * <p>A waypoint is reachable when either half holds:
 * <ul>
 *   <li>it belongs to a gang ({@link Waypoint#forGang()}) and the player is in that gang, or</li>
 *   <li>the player holds the waypoint's own permission node ({@link Waypoint#getPermission()}).</li>
 * </ul>
 *
 * <p>Observation #1 (lootchests-signs-waypoints.md), docket LS-01.
 */
public final class WaypointAccess {

	private WaypointAccess() {
	}

	/**
	 * Checks a single waypoint against the player.
	 *
	 * @param user the command caller; {@code null} denies (no cached user means no gang to compare)
	 * @param waypoint the waypoint being reached for; {@code null} denies
	 *
	 * @return {@code true} when the player may select, list or teleport to it
	 */
	public static boolean canAccess(@Nullable User<Player> user, @Nullable Waypoint waypoint) {
		if (user == null || waypoint == null) return false;

		Player player = user.getUser();

		if (player == null) return false;

		if (waypoint.forGang() && user.hasGang() && user.getGangId() == waypoint.getGangId()) return true;

		return player.hasPermission(waypoint.getPermission());
	}

	/**
	 * Filters a waypoint collection down to the ones {@code user} may reach, preserving iteration order.
	 *
	 * @return the accessible subset, never {@code null}
	 */
	public static List<Waypoint> accessible(@Nullable User<Player> user, Collection<Waypoint> waypoints) {
		if (user == null || waypoints == null) return List.of();

		return waypoints.stream().filter(waypoint -> canAccess(user, waypoint)).toList();
	}

}
