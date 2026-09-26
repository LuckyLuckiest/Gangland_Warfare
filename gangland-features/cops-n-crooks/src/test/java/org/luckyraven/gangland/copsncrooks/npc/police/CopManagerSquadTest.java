package org.luckyraven.gangland.copsncrooks.npc.police;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.CivilianNpcRegistry;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.police.targeting.TargetingManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase H11 (spec 4.9): the cops' squad learns where the wanted player is from the crime scene at wanted start, and
 * again whenever he hits one of the group's cops - never from an unrelated attacker.
 */
@DisplayName("CopManager - squad sightings")
class CopManagerSquadTest {

	private BukkitStatics bukkit;
	private World         world;
	private CopManager    manager;
	private Player        player;
	private UUID          playerId;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		// onWantedStart schedules the spawn and AI timers; the fixture's scheduler returns null for runTaskTimer
		when(bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
				.thenReturn(mock(BukkitTask.class));

		CopLoader copLoader = mock(CopLoader.class);
		when(copLoader.getLoadedProvider()).thenReturn(mock(CopConfigProvider.class));
		manager = new CopManager(mock(JavaPlugin.class), mock(CopSpawnManager.class), mock(TargetingManager.class),
		                         copLoader, mock(NpcMarkManager.class), mock(DetainmentService.class),
		                         mock(CivilianNpcRegistry.class));

		world    = mock(World.class);
		playerId = UUID.randomUUID();
		player   = mock(Player.class);
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getLocation()).thenReturn(new Location(world, 10, 64, 10));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("a wanted start reports the crime scene to the new group's squad")
	void wantedStart_reportsCrimeScene() {
		manager.onWantedStart(player, wanted());

		NpcSquad squad = manager.groupFor(playerId).getSquad();
		assertTrue(squad.hasFreshSighting());
		assertEquals(new Location(world, 10, 64, 10), squad.lastKnownLocation());
	}

	@Test
	@DisplayName("the group's target hitting a cop gives his position away; an unrelated attacker does not")
	void copAttacked_reportsOnlyTheGroupTarget() {
		manager.onWantedStart(player, wanted());
		CopNpc cop = mock(CopNpc.class);
		manager.groupFor(playerId).add(cop);

		Player stranger = mock(Player.class);
		when(stranger.getUniqueId()).thenReturn(UUID.randomUUID());
		when(stranger.getLocation()).thenReturn(new Location(world, 90, 64, 90));
		manager.onCopAttackedAlert(cop, stranger);

		assertEquals(new Location(world, 10, 64, 10), manager.groupFor(playerId).getSquad().lastKnownLocation());

		when(player.getLocation()).thenReturn(new Location(world, 40, 70, 40));
		manager.onCopAttackedAlert(cop, player);

		assertEquals(new Location(world, 40, 70, 40), manager.groupFor(playerId).getSquad().lastKnownLocation());
	}

	private static Wanted wanted() {
		Wanted wanted = mock(Wanted.class);
		when(wanted.isWanted()).thenReturn(true);
		return wanted;
	}
}
