package org.luckyraven.gangland.copsncrooks.npc.police.patrol;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.PatrolSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.station.Station;
import org.luckyraven.gangland.copsncrooks.station.StationRegistry;
import org.luckyraven.keystone.logging.Logger;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** T-314: the PAT-BEAT acceptance greps "PATROL station=<name> cops=0" when a beat despawns. */
@DisplayName("PatrolManager - beat despawn debug line")
class PatrolDespawnLogTest {

	private final List<String> lines = new ArrayList<>();
	private PatrolFixture                        fx;
	private org.apache.logging.log4j.core.Logger logger;
	private Level                                savedLevel;
	private AbstractAppender                     capture;

	@BeforeEach
	void setUp() {
		fx         = new PatrolFixture();
		logger     = (org.apache.logging.log4j.core.Logger) Logger.getLogger(PatrolManager.class);
		savedLevel = logger.getLevel();
		capture    = new AbstractAppender("patrol-capture", null, null, true) {
			@Override
			public void append(LogEvent event) {
				lines.add(event.getMessage().getFormattedMessage());
			}
		};
		capture.start();
		Configurator.setLevel(logger.getName(), Level.DEBUG);
		logger.addAppender(capture);
	}

	@AfterEach
	void tearDown() {
		logger.removeAppender(capture);
		Configurator.setLevel(logger.getName(), savedLevel);
		fx.close();
	}

	@Test
	@DisplayName("a beat that despawns after the idle window logs 'PATROL station=Central cops=0'")
	void despawnLogsZeroCops() {
		StationRegistry stations = mock(StationRegistry.class);
		CopSpawnManager spawner  = mock(CopSpawnManager.class);
		Location        anchor   = fx.at(0, 0);
		Station         station  = mock(Station.class);
		when(station.getId()).thenReturn(1);
		when(station.getName()).thenReturn("Central");
		when(station.getLocation()).thenReturn(anchor);
		when(stations.all()).thenReturn(List.of(station));
		when(spawner.spawnAtLocation(any(), anyInt())).thenAnswer(
				inv -> fx.cop(CopState.IDLE, 0, 0, inv.getArgument(0)));
		when(fx.bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
				.thenReturn(mock(BukkitTask.class));
		List<org.bukkit.entity.Player> online = new ArrayList<>();
		PatrolManager manager = new PatrolManager(mock(JavaPlugin.class), () -> PatrolSettings.DEFAULT, stations,
		                                          spawner, mock(NpcMarkManager.class), new CopPerception(),
		                                          () -> online, entity -> false, () -> true, () -> fx.clock[0]);

		online.add(fx.player(5, 5));
		manager.manage();
		online.clear();
		manager.manage();
		fx.clock[0] += 30_000;
		manager.manage();

		assertTrue(manager.beatCops().isEmpty());
		assertTrue(lines.contains("PATROL station=Central cops=0"), lines.toString());
	}
}
