package org.luckyraven.gangland.turf.listener;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.events.teleportation.TeleportEvent;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfRules;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.contract.TurfRepositoryContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.TurfState;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.TurfTextFixture;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** W5-T: gang teleports are vetoed around turf fights (PLAN 15.3 C2). The player's gang is 1; turfs span x 0..99, z 0..99. */
@DisplayName("TurfTeleportGuardListener - gang teleports and turf fights")
class TurfTeleportGuardListenerTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private World                     world;
	private TurfManager               turfs;
	private TurfMessages              text;
	private TurfTeleportGuardListener listener;
	private Player                    player;

	@BeforeEach
	void setUp() throws IOException {
		world = mock(World.class);
		when(world.getName()).thenReturn("world");
		turfs = new TurfManager(mock(TurfRepositoryContract.class));
		GangLookupContract gangs = mock(GangLookupContract.class);
		var gang1 = CaptureFixtures.gang(1);
		var gang2 = CaptureFixtures.gang(2);
		when(gangs.findById(1)).thenReturn(gang1);
		when(gangs.findById(2)).thenReturn(gang2);
		text = TurfTextFixture.messages(tempDir, mock(TurfMessageContract.class));
		TurfRules rules = TurfTextFixture.rules(tempDir, "Teleport_Guard:\n   Buffer_Blocks: 48\n");
		listener = new TurfTeleportGuardListener(turfs, gangs, rules, text);
		player   = CaptureFixtures.player();
		userRef  = CaptureFixtures.userInGang(1);
		when(userRef.getUser()).thenReturn(player);
	}

	private User<Player> userRef;

	private Turf turf(int id, String name, int minX, int maxX, Integer owner, boolean underAttack) {
		Turf turf = new Turf(id, name, new CuboidRegion("world", minX, 0, maxX, 99), owner, BigDecimal.TEN, 0L, 0L);
		turfs.create(turf);
		if (underAttack) {
			turfs.getRuntimeState(id).setState(TurfState.CONTESTING);
			turfs.getRuntimeState(id).setChallengerGangId(owner != null && owner == 2 ? 3 : 2);
		}
		return turf;
	}

	private TeleportEvent event(Waypoint.WaypointType type, int fromX, int toX) {
		Waypoint waypoint = mock(Waypoint.class);
		when(waypoint.getType()).thenReturn(type);
		when(waypoint.getLocation()).thenReturn(new Location(world, toX, 64, 50));
		return new TeleportEvent(userRef, new Location(world, fromX, 64, 50), waypoint);
	}

	@Test
	@DisplayName("sourceUnderAttack: leaving a turf under attack is refused")
	void sourceUnderAttack() {
		turf(1, "Alpha", 0, 99, 1, true);
		TeleportEvent event = event(Waypoint.WaypointType.GANG, 10, 5000);

		listener.onTeleport(event);

		assertTrue(event.isCancelled());
		verify(player).sendMessage(text.text("Teleport_Guard.Source_Under_Attack", "", "turf", "Alpha"));
	}

	@Test
	@DisplayName("destinationUnderAttack: landing inside a turf under attack is refused")
	void destinationUnderAttack() {
		turf(1, "Alpha", 0, 99, 2, true);
		TeleportEvent event = event(Waypoint.WaypointType.GANG, 5000, 10);

		listener.onTeleport(event);

		assertTrue(event.isCancelled());
		verify(player).sendMessage(text.text("Teleport_Guard.Destination_Under_Attack", "", "turf", "Alpha"));
	}

	@Test
	@DisplayName("destinationOneBlockOutside: one block past the edge is inside the 48-block buffer")
	void destinationOneBlockOutside() {
		turf(1, "Alpha", 0, 99, 2, true);
		TeleportEvent event = event(Waypoint.WaypointType.GANG, 5000, 100);

		listener.onTeleport(event);

		assertTrue(event.isCancelled());
	}

	@Test
	@DisplayName("destinationBeyondBuffer: 49 blocks past the edge of a turf under attack is allowed")
	void destinationBeyondBuffer() {
		turf(1, "Alpha", 0, 99, 2, true);
		TeleportEvent event = event(Waypoint.WaypointType.GANG, 5000, 99 + 49);

		listener.onTeleport(event);

		assertFalse(event.isCancelled());
	}

	@Test
	@DisplayName("destinationRival: landing in a turf owned by a gang that is neither yours nor allied is refused")
	void destinationRival() {
		turf(1, "Alpha", 0, 99, 2, false);
		TeleportEvent event = event(Waypoint.WaypointType.GANG, 5000, 10);

		listener.onTeleport(event);

		assertTrue(event.isCancelled());
		verify(player).sendMessage(text.text("Teleport_Guard.Destination_Rival", "", "turf", "Alpha"));
	}

	@Test
	@DisplayName("gangInAnyContest: a gang that challenges any turf teleports nowhere")
	void gangInAnyContest() {
		Turf far = turf(1, "Bravo", 1000, 1099, 2, false);
		turfs.getRuntimeState(far.getId()).setState(TurfState.CONTESTING);
		turfs.getRuntimeState(far.getId()).setChallengerGangId(1);
		TeleportEvent event = event(Waypoint.WaypointType.GANG, 5000, 6000);

		listener.onTeleport(event);

		assertTrue(event.isCancelled());
		verify(player).sendMessage(text.text("Teleport_Guard.Gang_In_Fight", "", "turf", "Bravo"));
	}

	@Test
	@DisplayName("ownQuietTurf: teleporting home into an own quiet turf is allowed")
	void ownQuietTurf() {
		turf(1, "Alpha", 0, 99, 1, false);
		TeleportEvent event = event(Waypoint.WaypointType.GANG, 5000, 10);

		listener.onTeleport(event);

		assertFalse(event.isCancelled());
		verifyNoInteractions(player);
	}

	@Test
	@DisplayName("nonGangWaypoint: a SPAWN waypoint into a turf under attack is untouched")
	void nonGangWaypoint() {
		turf(1, "Alpha", 0, 99, 2, true);
		TeleportEvent event = event(Waypoint.WaypointType.SPAWN, 5000, 10);

		listener.onTeleport(event);

		assertFalse(event.isCancelled());
	}
}
