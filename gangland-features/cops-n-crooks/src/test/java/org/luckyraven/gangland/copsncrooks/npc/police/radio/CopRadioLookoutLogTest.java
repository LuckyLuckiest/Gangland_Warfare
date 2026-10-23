package org.luckyraven.gangland.copsncrooks.npc.police.radio;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.keystone.logging.Logger;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** LOOK-RELAY/LOOK-WANTED acceptance greps the debug line "LOOKOUT hearer -> hunted" from CopRadio. */
@DisplayName("CopRadio - lookout debug line")
class CopRadioLookoutLogTest {

	private final List<String> lines = new ArrayList<>();
	private BukkitStatics                         bukkit;
	private org.apache.logging.log4j.core.Logger  logger;
	private Level                                 savedLevel;
	private String                                loggerName;
	private AbstractAppender                      capture;

	@BeforeEach
	void setUp() {
		bukkit     = BukkitStatics.install();
		logger     = (org.apache.logging.log4j.core.Logger) Logger.getLogger(CopRadio.class);
		loggerName = logger.getName();
		savedLevel = logger.getLevel();
		capture    = new AbstractAppender("lookout-capture", null, null, true) {
			@Override
			public void append(LogEvent event) {
				lines.add(event.getMessage().getFormattedMessage());
			}
		};
		capture.start();
		Configurator.setLevel(loggerName, Level.DEBUG);
		logger.addAppender(capture);
	}

	@AfterEach
	void tearDown() {
		logger.removeAppender(capture);
		Configurator.setLevel(loggerName, savedLevel);
		bukkit.close();
	}

	private Player player(World world, String name) {
		UUID   id = UUID.randomUUID();
		Player p  = mock(Player.class);
		when(p.getUniqueId()).thenReturn(id);
		when(p.getName()).thenReturn(name);
		when(p.getWorld()).thenReturn(world);
		when(p.getLocation()).thenReturn(new Location(world, 0, 64, 0));
		bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(p);
		return p;
	}

	@Test
	@DisplayName("relaysTo logs 'LOOKOUT <hearer> -> <hunted>' when the predicate is true, nothing when false")
	void relaysTo_logsDebugLine() {
		World             world    = mock(World.class);
		CopConfigProvider provider = mock(CopConfigProvider.class);
		CopRadio          radio    = new CopRadio(() -> provider, key -> List.of(key), () -> 10_000L, (t, k) -> { });
		Player            suspect  = player(world, "Suspect");
		Player            hearer   = player(world, "Mate");
		CopGroup          group    = new CopGroup(suspect.getUniqueId());

		radio.setLookout((h, t) -> false);
		radio.voice(group).relaysTo(hearer, group.getSquad());
		assertTrue(lines.stream().noneMatch(l -> l.startsWith("LOOKOUT")), "false predicate logs nothing");

		radio.setLookout((h, t) -> true);
		radio.voice(group).relaysTo(hearer, group.getSquad());
		assertEquals(List.of("LOOKOUT Mate -> Suspect"), lines.stream().filter(l -> l.startsWith("LOOKOUT")).toList());
	}
}
