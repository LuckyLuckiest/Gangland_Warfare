package org.luckyraven.gangland.copsncrooks.hud;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.evasion.EvasionService;
import org.luckyraven.gangland.copsncrooks.evasion.EvasionSnapshot;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 0.12 review: {@code WantedLevelChangeEvent} fires (and so reaches {@code WantedHudListener}) before
 * {@code Wanted#setLevel} applies the new level to the {@code Wanted} object itself. {@link WantedHudService#show}
 * used to always read {@link Wanted#getLevel()}, so a star gain/loss between two non-zero levels rendered the stale,
 * pre-change level on the boss bar until the next 20-tick pass. {@link WantedHudService#onStarGained} and
 * {@link WantedHudService#onStarLost} must render the level they are handed instead.
 */
@DisplayName("WantedHudService")
class WantedHudServiceTest {

	private BukkitStatics          bukkit;
	private MockedStatic<Settings> settings;

	private BossBar              bar;
	private Player               player;
	private UUID                 playerId;
	private UserManager<Player>  users;
	private WantedHudService     service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		bukkit = BukkitStatics.install();
		when(bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
				.thenReturn(mock(BukkitTask.class));

		bar = mock(BossBar.class);
		bukkit.statics().when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
		      .thenReturn(bar);

		settings = mockStatic(Settings.class);
		settings.when(Settings::getMoneySymbol).thenReturn("$");

		playerId = UUID.randomUUID();
		player   = mock(Player.class);
		when(player.getUniqueId()).thenReturn(playerId);

		EvasionService evasion = mock(EvasionService.class);
		when(evasion.getSnapshot(playerId)).thenReturn(EvasionSnapshot.none());

		CopLoader copLoader = mock(CopLoader.class);
		when(copLoader.getLoadedHudConfig()).thenReturn(HudConfig.defaults());

		users   = mock(UserManager.class);
		service = new WantedHudService(mock(JavaPlugin.class), evasion, users, copLoader);
	}

	@AfterEach
	void tearDown() {
		settings.close();
		bukkit.close();
	}

	@Test
	@DisplayName("onStarGained renders the new level it is handed, not Wanted#getLevel() (0.12 review regression)")
	void onStarGained_rendersHandedLevel_notStaleWantedLevel() {
		// Simulates WantedLevelChangeEvent 2 -> 3: the live Wanted object still reports the pre-change level (2)
		// because Wanted#setLevel has not yet applied the new level when this fires.
		trackWithStaleLevel(2);

		service.onStarGained(player, 3, 5);

		String title = capturedTitle();
		assertTrue(title.contains(Wanted.buildStars(3, 5)), title);
		assertFalse(title.contains(Wanted.buildStars(2, 5)), title);
	}

	@Test
	@DisplayName("onStarLost renders the new (lower) level it is handed, not Wanted#getLevel() (0.12 review regression)")
	void onStarLost_rendersHandedLevel_notStaleWantedLevel() {
		// Simulates WantedLevelChangeEvent 3 -> 2: the live Wanted object still reports the pre-change level (3).
		trackWithStaleLevel(3);

		service.onStarLost(player, 2, 5);

		String title = capturedTitle();
		assertTrue(title.contains(Wanted.buildStars(2, 5)), title);
		assertFalse(title.contains(Wanted.buildStars(3, 5)), title);
	}

	@SuppressWarnings("unchecked")
	private void trackWithStaleLevel(int staleLevel) {
		Wanted wanted = new Wanted(mock(JavaPlugin.class), 1, 5); // no owner: setLevel fires no events
		wanted.setLevel(staleLevel);

		User<Player> user = mock(User.class);
		when(user.getWanted()).thenReturn(wanted);
		when(users.getUser(player)).thenReturn(user);
	}

	private String capturedTitle() {
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(bar, atLeastOnce()).setTitle(captor.capture());
		return captor.getValue();
	}

}
