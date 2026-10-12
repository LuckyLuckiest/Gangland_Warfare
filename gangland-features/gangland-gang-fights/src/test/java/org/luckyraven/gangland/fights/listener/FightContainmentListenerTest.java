package org.luckyraven.gangland.fights.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.fights.fight.Fight;
import org.luckyraven.gangland.fights.fight.FightState;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FightContainmentListener - fighters stay in, outsiders stay out, blocks stay put (GP-31)")
class FightContainmentListenerTest {

	private FightRig                  rig;
	private FightContainmentListener listener;
	private Player                    outsider;

	@BeforeEach
	void setUp() {
		rig      = new FightRig();
		listener = new FightContainmentListener(null, rig.manager, rig.arenas, rig.rules, rig.messages);
		outsider = rig.player();
		when(outsider.getLocation()).thenReturn(rig.at(100, 1, 100));
		when(rig.a.getLocation()).thenReturn(rig.at(2, 1, 2));
	}

	private PlayerMoveEvent move(Player player, Location from, Location to) {
		PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
		listener.onMove(event);
		return event;
	}

	@Test
	@DisplayName("a fighter stepping out of the box is put back where he came from; a step inside, or a head turn, passes")
	void fighterMove() {
		rig.running(0);
		Location inside  = rig.at(5, 1, 5);
		Location outside = rig.at(21.5, 1, 5);

		PlayerMoveEvent out = move(rig.a, inside, outside);
		PlayerMoveEvent in  = move(rig.a, inside, rig.at(6, 1, 5));

		assertEquals(inside, out.getTo());
		assertEquals(6D, in.getTo().getX());
	}

	@Test
	@DisplayName("an outsider is refused entry to a busy arena but may walk past an idle one")
	void outsider_refusedAndEjected() {
		Location edge   = rig.at(21.5, 1, 5);
		Location inside = rig.at(20.5, 1, 5);

		assertEquals(inside, move(outsider, inside, rig.at(20.5, 1, 5)).getTo(), "idle arena: no fight, no limit");
		rig.running(0);
		PlayerMoveEvent refused = move(outsider, edge, inside);
		assertEquals(edge, refused.getTo());

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(rig.world);
			when(outsider.getLocation()).thenReturn(rig.at(10, 1, 10));

			listener.check(List.of(outsider));

			ArgumentCaptor<Location> to = ArgumentCaptor.forClass(Location.class);
			verify(outsider).teleport(to.capture());
			assertEquals(rig.exit.x(), to.getValue().getX());
		}
	}

	@Test
	@DisplayName("a fighter's teleport out of the box is refused unless the fight itself makes it; the result pause lets him go home")
	void teleportGate() {
		Fight fight = rig.running(0);
		Location outside = rig.at(100, 1, 100);

		PlayerTeleportEvent pearl = new PlayerTeleportEvent(rig.a, rig.at(5, 1, 5), outside,
		                                                    PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
		listener.onTeleport(pearl);
		assertTrue(pearl.isCancelled());

		PlayerTeleportEvent waypoint = new PlayerTeleportEvent(rig.a, rig.at(5, 1, 5), outside,
		                                                       PlayerTeleportEvent.TeleportCause.PLUGIN);
		listener.onTeleport(waypoint);
		assertTrue(waypoint.isCancelled(), "a PLUGIN teleport is not the fight's own");

		PlayerTeleportEvent ours = new PlayerTeleportEvent(rig.a, rig.at(5, 1, 5), outside,
		                                                   PlayerTeleportEvent.TeleportCause.PLUGIN);
		ExpectedTeleports.run(rig.a.getUniqueId(), () -> {
			listener.onTeleport(ours);
			return true;
		});
		assertFalse(ours.isCancelled());
		assertFalse(ExpectedTeleports.expected(rig.a.getUniqueId()), "the mark never outlives the teleport");

		PlayerTeleportEvent within = new PlayerTeleportEvent(rig.a, rig.at(5, 1, 5), rig.at(8, 1, 8),
		                                                     PlayerTeleportEvent.TeleportCause.COMMAND);
		listener.onTeleport(within);
		assertFalse(within.isCancelled());

		fight.state(FightState.RESULT);
		PlayerTeleportEvent home = new PlayerTeleportEvent(rig.a, rig.at(5, 1, 5), outside,
		                                                   PlayerTeleportEvent.TeleportCause.PLUGIN);
		listener.onTeleport(home);
		assertFalse(home.isCancelled());
	}

	@Test
	@DisplayName("an outsider cannot teleport into a busy arena; a fighter cannot use a portal")
	void outsiderTeleportAndPortal() {
		rig.running(0);

		PlayerTeleportEvent in = new PlayerTeleportEvent(outsider, rig.at(100, 1, 100), rig.at(10, 1, 10),
		                                                 PlayerTeleportEvent.TeleportCause.COMMAND);
		listener.onTeleport(in);
		assertTrue(in.isCancelled());

		PlayerPortalEvent portal = new PlayerPortalEvent(rig.a, rig.at(5, 1, 5), rig.at(5, 1, 5));
		listener.onPortal(portal);
		assertTrue(portal.isCancelled());
	}

	@Test
	@DisplayName("block place and break inside a busy arena are refused for everyone, and allowed outside it or when Protect_Blocks is off")
	void blockPlace_refusedForEveryone() {
		Block inside = mock(Block.class);
		when(inside.getLocation()).thenReturn(rig.at(10, 1, 10));
		Block far = mock(Block.class);
		when(far.getLocation()).thenReturn(rig.at(100, 1, 100));

		BlockBreakEvent idle = new BlockBreakEvent(inside, outsider);
		listener.onBlockBreak(idle);
		assertFalse(idle.isCancelled(), "no fight, no protection");

		rig.running(0);
		BlockBreakEvent broken = new BlockBreakEvent(inside, outsider);
		listener.onBlockBreak(broken);
		assertTrue(broken.isCancelled());

		BlockPlaceEvent placed = mock(BlockPlaceEvent.class);
		when(placed.getBlock()).thenReturn(inside);
		when(placed.getPlayer()).thenReturn(rig.a);
		listener.onBlockPlace(placed);
		verify(placed).setCancelled(true);

		BlockBreakEvent faraway = new BlockBreakEvent(far, outsider);
		listener.onBlockBreak(faraway);
		assertFalse(faraway.isCancelled());

		when(rig.rules.isProtectBlocks()).thenReturn(false);
		BlockBreakEvent unprotected = new BlockBreakEvent(inside, outsider);
		listener.onBlockBreak(unprotected);
		assertFalse(unprotected.isCancelled());
	}

	@Test
	@DisplayName("a fighter cannot board a vehicle unless Allow_Vehicles is on, and is dismounted by the check")
	void vehicles() {
		rig.running(0);
		VehicleEnterEvent boarding = new VehicleEnterEvent(mock(Vehicle.class), rig.a);
		listener.onVehicleEnter(boarding);
		assertTrue(boarding.isCancelled());

		when(rig.a.isInsideVehicle()).thenReturn(true);
		listener.check(List.of(rig.a));
		verify(rig.a).leaveVehicle();

		when(rig.rules.isAllowVehicles()).thenReturn(true);
		VehicleEnterEvent allowed = new VehicleEnterEvent(mock(Vehicle.class), rig.a);
		listener.onVehicleEnter(allowed);
		assertFalse(allowed.isCancelled());
	}

	@Test
	@DisplayName("a fighter found outside by the 5-tick check is put back and warned three times, then forfeits")
	void leaveWarnings_thenForfeit() {
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(rig.world);
			Fight fight = rig.running(0);
			when(rig.a.getLocation()).thenReturn(rig.at(30, 1, 30));

			for (int i = 0; i < 3; i++) {
				listener.check(List.of(rig.a));
				assertFalse(fight.eliminated(rig.a.getUniqueId()), "warning " + (i + 1));
			}
			verify(rig.a, org.mockito.Mockito.times(3)).teleport(any(Location.class));

			listener.check(List.of(rig.a));

			assertTrue(fight.eliminated(rig.a.getUniqueId()));
		}
	}

	@Test
	@DisplayName("a fighter inside the box is never warned, and the warning count starts over")
	void insideIsQuiet() {
		Fight fight = rig.running(0);

		for (int i = 0; i < 6; i++) {
			listener.check(List.of(rig.a));
		}

		assertFalse(fight.eliminated(rig.a.getUniqueId()));
		verify(rig.a, never()).teleport(any(Location.class));
	}
}
