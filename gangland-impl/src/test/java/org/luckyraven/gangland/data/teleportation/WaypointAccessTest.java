package org.luckyraven.gangland.data.teleportation;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.user.User;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link WaypointAccess}: the permission/gang rule the waypoint tab-completers already applied, now applied to
 * the actions themselves. Before the fix {@code /glw teleport <name>}, {@code /glw waypoint select <id>} and
 * {@code /glw waypoint list} ignored it entirely.
 *
 * <p>Observation #1 (lootchests-signs-waypoints.md), docket LS-01.
 */
@DisplayName("WaypointAccess - who may see, select and teleport to a waypoint")
class WaypointAccessTest {

	private static Waypoint waypoint(String name, int gangId) {
		Waypoint waypoint = new Waypoint(name, "gangland");
		waypoint.setGangId(gangId);
		return waypoint;
	}

	@SuppressWarnings("unchecked")
	private static User<Player> user(Player player, boolean hasGang, int gangId) {
		User<Player> user = mock(User.class);
		when(user.getUser()).thenReturn(player);
		when(user.hasGang()).thenReturn(hasGang);
		when(user.getGangId()).thenReturn(gangId);
		return user;
	}

	@Test
	@DisplayName("a permission-gated waypoint is unreachable without the node - the LS-01 defect")
	void canAccess_deniesWithoutPermissionOrGang() {
		Waypoint hideout = waypoint("hideout", -1);

		Player player = mock(Player.class);
		when(player.hasPermission(hideout.getPermission())).thenReturn(false);

		assertFalse(WaypointAccess.canAccess(user(player, false, -1), hideout));
	}

	@Test
	void canAccess_allowsWhenPlayerHoldsTheWaypointNode() {
		Waypoint spawn = waypoint("spawn", -1);

		Player player = mock(Player.class);
		when(player.hasPermission(spawn.getPermission())).thenReturn(true);

		assertTrue(WaypointAccess.canAccess(user(player, false, -1), spawn));
	}

	@Test
	@DisplayName("a gang waypoint is reachable by that gang without any permission node")
	void canAccess_allowsOwnGangWaypoint() {
		Waypoint base = waypoint("base", 7);

		Player player = mock(Player.class);
		when(player.hasPermission(base.getPermission())).thenReturn(false);

		assertTrue(WaypointAccess.canAccess(user(player, true, 7), base));
	}

	@Test
	@DisplayName("another gang's base stays closed - this is the teleport-into-their-base hole")
	void canAccess_deniesOtherGangWaypoint() {
		Waypoint base = waypoint("rival-base", 7);

		Player player = mock(Player.class);
		when(player.hasPermission(base.getPermission())).thenReturn(false);

		assertFalse(WaypointAccess.canAccess(user(player, true, 9), base));
	}

	@Test
	void canAccess_deniesForNullUserOrWaypoint() {
		Player player = mock(Player.class);

		assertFalse(WaypointAccess.canAccess(null, waypoint("x", -1)));
		assertFalse(WaypointAccess.canAccess(user(player, false, -1), null));
	}

	@Test
	@DisplayName("accessible() keeps order and drops everything the player may not reach")
	void accessible_filtersTheList() {
		Waypoint ownGang  = waypoint("own", 3);
		Waypoint rival    = waypoint("rival", 4);
		Waypoint publicWp = waypoint("public", -1);
		Waypoint locked   = waypoint("locked", -1);

		Player player = mock(Player.class);
		when(player.hasPermission(publicWp.getPermission())).thenReturn(true);

		List<Waypoint> visible = WaypointAccess.accessible(user(player, true, 3),
		                                                   List.of(ownGang, rival, publicWp, locked));

		assertEquals(List.of(ownGang, publicWp), visible);
	}

	@Test
	void accessible_nullUserOrCollectionYieldsEmptyList() {
		assertTrue(WaypointAccess.accessible(null, List.of(waypoint("x", -1))).isEmpty());
		assertTrue(WaypointAccess.accessible(user(mock(Player.class), false, -1), null).isEmpty());
	}

}
