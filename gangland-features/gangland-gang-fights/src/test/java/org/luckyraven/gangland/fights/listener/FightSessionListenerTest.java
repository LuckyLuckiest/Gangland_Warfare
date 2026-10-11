package org.luckyraven.gangland.fights.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.fights.fight.Fight;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FightSessionListener - quit forfeits, join lands at the exit, a stray death keeps everything (GP-31, GP-41)")
class FightSessionListenerTest {

	private FightRig             rig;
	private FightSessionListener listener;

	@BeforeEach
	void setUp() {
		rig      = new FightRig();
		listener = new FightSessionListener(null, rig.manager, rig.arenas, rig.messages);
	}

	@Test
	@DisplayName("a fighter who quits mid-fight is eliminated and restored while still online")
	void quit_forfeitsAndRestores() {
		Fight fight = rig.running(0);

		listener.onQuit(new PlayerQuitEvent(rig.a, "bye"));

		assertTrue(fight.eliminated(rig.a.getUniqueId()));
		verify(rig.fightWorld).restore(any());
	}

	@Test
	@DisplayName("quitting from the lobby drops the fighter from the roster")
	void quit_inLobby_dropsFromRoster() {
		rig.manager.propose(rig.a.getUniqueId(), "Rats", 1, 0);
		rig.manager.accept(rig.b.getUniqueId(), "Vipers");
		rig.manager.enlist(rig.a.getUniqueId());

		listener.onQuit(new PlayerQuitEvent(rig.a, "bye"));

		assertFalse(rig.manager.isParticipant(rig.a.getUniqueId()));
	}

	@Test
	@DisplayName("a player who quits while in no fight changes nothing")
	void quit_outsider_ignored() {
		rig.running(0);

		listener.onQuit(new PlayerQuitEvent(rig.player(), "bye"));

		verify(rig.fightWorld, never()).restore(any());
	}

	@Test
	@DisplayName("a player who joins standing inside an arena is sent to its exit; one outside, or arena staff, is left alone")
	void join_ejectedToExit() {
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(rig.world);
			when(rig.a.getLocation()).thenReturn(rig.at(10, 1, 10));
			when(rig.b.getLocation()).thenReturn(rig.at(500, 1, 500));
			var staff = rig.player();
			when(staff.getLocation()).thenReturn(rig.at(10, 1, 10));
			when(staff.hasPermission("gangland.arena.admin")).thenReturn(true);

			listener.onJoin(new PlayerJoinEvent(rig.a, "hi"));
			listener.onJoin(new PlayerJoinEvent(rig.b, "hi"));
			listener.onJoin(new PlayerJoinEvent(staff, "hi"));

			ArgumentCaptor<Location> to = ArgumentCaptor.forClass(Location.class);
			verify(rig.a).teleport(to.capture());
			assertEquals(rig.exit.x(), to.getValue().getX());
			verify(rig.b, never()).teleport(any(Location.class));
			verify(staff, never()).teleport(any(Location.class));
		}
	}

	@Test
	@DisplayName("a death that skipped the damage event keeps inventory and level and drops nothing")
	void death_fallbackKeepsEverything() {
		rig.running(0);
		List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(org.bukkit.Material.DIAMOND)));
		PlayerDeathEvent death = new PlayerDeathEvent(rig.a, drops, 50, "died");

		listener.onDeath(death);

		assertTrue(death.getKeepInventory());
		assertTrue(death.getKeepLevel());
		assertTrue(death.getDrops().isEmpty());
		assertEquals(0, death.getDroppedExp());
	}

	@Test
	@DisplayName("a death outside any fight is not touched")
	void death_outsider_untouched() {
		rig.running(0);
		List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(org.bukkit.Material.DIAMOND)));
		PlayerDeathEvent death = new PlayerDeathEvent(rig.player(), drops, 50, "died");

		listener.onDeath(death);

		assertFalse(death.getKeepInventory());
		assertEquals(1, death.getDrops().size());
	}

	@Test
	@DisplayName("the dead fighter respawns at the arena exit and is eliminated")
	void respawn_atTheExit() {
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(rig.world);
			Fight fight = rig.running(0);
			listener.onDeath(new PlayerDeathEvent(rig.a, new ArrayList<>(), 0, "died"));
			PlayerRespawnEvent respawn = new PlayerRespawnEvent(rig.a, rig.at(0, 0, 0), false);

			listener.onRespawn(respawn);

			assertEquals(rig.exit.x(), respawn.getRespawnLocation().getX());
			assertTrue(fight.eliminated(rig.a.getUniqueId()));
		}
	}
}
