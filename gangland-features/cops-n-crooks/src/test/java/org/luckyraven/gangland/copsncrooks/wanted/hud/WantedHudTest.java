package org.luckyraven.gangland.copsncrooks.wanted.hud;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.HudSettings;
import org.luckyraven.gangland.events.wanted.EvasionState;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WantedHud}: the bar's colour and title per chase state, the green flash, hide/late-event behaviour, the zone
 * ring, the compass, and each switch removing exactly its own piece.
 */
@DisplayName("WantedHud")
class WantedHudTest {

	@TempDir
	Path tempDir;

	private BukkitStatics bukkit;
	private BossBar       bar;
	private World         world;
	private WantedMessages messages;

	@BeforeAll
	static void prime() throws ReflectiveOperationException {
		HudFixtures.primeMoneySymbol();
	}

	@BeforeEach
	void setUp() throws IOException {
		bukkit = BukkitStatics.install();
		bar    = mock(BossBar.class);
		bukkit.statics()
		      .when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
		      .thenReturn(bar);
		world    = mock(World.class);
		messages = HudFixtures.messages(tempDir);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private Player player() {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(new Location(world, 110, 64, 100));
		when(player.getCompassTarget()).thenReturn(new Location(world, 1, 2, 3));
		return player;
	}

	private WantedHud hud(HudSettings settings) {
		return new WantedHud(HudFixtures.loader(settings), messages);
	}

	private WantedHud hud() {
		return hud(HudSettings.DEFAULT);
	}

	private Location centre() {
		return new Location(world, 100, 64, 100);
	}

	@Test
	@DisplayName("show gives that player a red bar with the SEEN text")
	void show_createsARedBarForThatPlayer() {
		Player player = player();

		hud().show(player, 2, "**");

		bukkit.statics().verify(() -> Bukkit.createBossBar(anyString(), eq(BarColor.RED), eq(BarStyle.SOLID)));
		verify(bar).addPlayer(player);
		verify(bar).setColor(BarColor.RED);
		verify(bar).setProgress(1.0);
	}

	@Test
	@DisplayName("searching is yellow with the countdown and the progress left, and flashes white on the next beat")
	void searching_turnsYellowWithTheCountdown_andFlashes() {
		Player  player = player();
		WantedHud hud  = hud();
		hud.show(player, 2, "**");

		hud.state(player, EvasionState.SEARCHING, 2, 10, centre(), 60);

		verify(bar).setColor(BarColor.YELLOW);
		// level 2 drops after 20 s: 10 s left is half the bar
		verify(bar).setProgress(0.5);
		verify(bar).setTitle(org.mockito.ArgumentMatchers.contains("SEARCHING 10s"));

		hud.tick();

		verify(bar).setColor(BarColor.WHITE);
	}

	@Test
	@DisplayName("a sighting while searching turns the bar red again")
	void searchingThenSeen_turnsRedAgain() {
		Player  player = player();
		WantedHud hud  = hud();
		hud.show(player, 2, "**");
		hud.state(player, EvasionState.SEARCHING, 2, 10, centre(), 60);

		hud.state(player, EvasionState.SEEN, 2, 0, null, 0);

		verify(bar, times(2)).setColor(BarColor.RED);
		verify(bar, times(2)).setProgress(1.0);
	}

	@Test
	@DisplayName("a lost star is green for three seconds, then the current state returns")
	void evaded_isGreenForThreeSeconds() {
		Player  player = player();
		WantedHud hud  = hud();
		hud.show(player, 2, "**");
		hud.state(player, EvasionState.EVADED, 1, 0, centre(), 40);
		verify(bar).setColor(BarColor.GREEN);

		// the clock re-enters SEARCHING straight away; the HUD keeps the green until the three seconds are up
		hud.state(player, EvasionState.SEARCHING, 1, 10, centre(), 40);
		for (int i = 0; i < 5; i++) {
			hud.tick();
		}
		verify(bar, never()).setColor(BarColor.YELLOW);

		hud.tick();
		hud.tick();

		verify(bar, times(1)).setColor(BarColor.YELLOW);
	}

	@Test
	@DisplayName("hide removes the bar and gives the compass back")
	void hide_removesTheBar_andRestoresTheCompass() {
		Player    player = player();
		Location  saved  = player.getCompassTarget();
		WantedHud hud    = hud();
		hud.show(player, 2, "**");
		hud.state(player, EvasionState.SEARCHING, 2, 10, centre(), 60);

		hud.hide(player);

		verify(bar).removeAll();
		verify(player).setCompassTarget(saved);
	}

	@Test
	@DisplayName("a state event after hide creates no bar")
	void stateAfterHide_createsNoBar() {
		Player  player = player();
		WantedHud hud  = hud();
		hud.show(player, 2, "**");
		hud.hide(player);

		hud.state(player, EvasionState.SEARCHING, 2, 10, centre(), 60);
		hud.tick();
		hud.tick();

		bukkit.statics().verify(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)),
		                        times(1));
		verify(player, never()).setCompassTarget(any(Location.class));
	}

	@Test
	@DisplayName("searching draws the configured number of ring points to that player only")
	void searching_spawnsZoneRingPointsForThatPlayerOnly() {
		Player    player = player();
		Player    other  = player();
		WantedHud hud    = hud();
		hud.show(player, 2, "**");
		hud.show(other, 2, "**");
		hud.state(player, EvasionState.SEARCHING, 2, 10, centre(), 60);

		hud.tick();
		hud.tick();

		verify(player, times(HudSettings.DEFAULT.zonePoints())).spawnParticle(any(Particle.class),
		                                                                       any(Location.class), eq(1),
		                                                                       anyDouble(), anyDouble(),
		                                                                       anyDouble(), anyDouble(), any());
		verify(other, never()).spawnParticle(any(Particle.class), any(Location.class), anyInt(), anyDouble(),
		                                     anyDouble(), anyDouble(), anyDouble(), any());
	}

	@Test
	@DisplayName("with Zone_Ring off no particle is spawned")
	void zoneRingDisabled_spawnsNothing() {
		Player    player = player();
		WantedHud hud    = hud(HudFixtures.hud(true, true, true, true, false, true));
		hud.show(player, 2, "**");
		hud.state(player, EvasionState.SEARCHING, 2, 10, centre(), 60);

		hud.tick();
		hud.tick();

		verify(player, never()).spawnParticle(any(Particle.class), any(Location.class), anyInt(), anyDouble(),
		                                      anyDouble(), anyDouble(), anyDouble(), any());
	}

	@Test
	@DisplayName("searching points the compass at the zone edge towards the player")
	void searching_retargetsTheCompassToTheExitPoint() {
		Player    player = player();
		WantedHud hud    = hud();
		hud.show(player, 2, "**");

		hud.state(player, EvasionState.SEARCHING, 2, 10, centre(), 60);

		// player at x=110, centre x=100, radius 60: the edge towards them is x=160
		org.mockito.ArgumentCaptor<Location> target = org.mockito.ArgumentCaptor.forClass(Location.class);
		verify(player).setCompassTarget(target.capture());
		assertEquals(160, target.getValue().getX(), 1.0E-9);
		assertEquals(100, target.getValue().getZ(), 1.0E-9);
	}

	@Test
	@DisplayName("with Compass off the compass is never touched")
	void compassDisabled_leavesTheCompassAlone() {
		Player    player = player();
		WantedHud hud    = hud(HudFixtures.hud(true, true, true, true, true, false));
		hud.show(player, 2, "**");

		hud.state(player, EvasionState.SEARCHING, 2, 10, centre(), 60);
		hud.hide(player);

		verify(player, never()).setCompassTarget(any(Location.class));
		verify(player, never()).getCompassTarget();
	}

	@Test
	@DisplayName("with Boss_Bar off no bar is created but the ring still draws")
	void bossBarDisabled_createsNoBar() {
		Player    player = player();
		WantedHud hud    = hud(HudFixtures.hud(false, true, true, true, true, true));
		hud.show(player, 2, "**");
		hud.state(player, EvasionState.SEARCHING, 2, 10, centre(), 60);

		hud.tick();
		hud.tick();

		bukkit.statics().verify(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)),
		                        never());
		verify(player, times(HudSettings.DEFAULT.zonePoints())).spawnParticle(any(Particle.class),
		                                                                       any(Location.class), anyInt(),
		                                                                       anyDouble(), anyDouble(),
		                                                                       anyDouble(), anyDouble(), any());
	}
}
