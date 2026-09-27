package org.luckyraven.gangland.copsncrooks.evasion;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.events.evasion.EvasionStateChangeEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedSettings;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 0.12 F2: evasion drives a chase only for a tracked player whose cop group has had a cop, drops a star when the clock
 * runs out (with the usual wanted-decreased message), and reports every state change.
 */
@DisplayName("EvasionService")
class EvasionServiceTest {

	private BukkitStatics          bukkit;
	private MockedStatic<Settings> settings;
	private BukkitTask             task;
	private CopManager             copManager;
	private CopGroup               group;
	private Player                 player;
	private UUID                   playerId;
	private Wanted                 wanted;
	private User<Player>           user;
	private EvasionService         service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		bukkit = BukkitStatics.install();
		task   = mock(BukkitTask.class);
		when(bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
				.thenReturn(task);

		settings = mockStatic(Settings.class);
		settings.when(Settings::isWantedEvasionEnabled).thenReturn(true);
		settings.when(Settings::getWantedEvasionLostSightSeconds).thenReturn(3);
		settings.when(Settings::getWantedEvasionDropMode).thenReturn("ONE_STAR");
		settings.when(Settings::getWantedEvasionSearchRadius).thenReturn(List.of(40, 60, 90, 130, 180));
		settings.when(Settings::getWantedEvasionSecondsToDrop).thenReturn(List.of(2, 2, 2, 2, 2));
		settings.when(Settings::getWantedEvasionOutsideZoneSpeed).thenReturn(2.0);
		settings.when(Settings::getWantedEvasionHideoutSpeed).thenReturn(1.5);

		playerId = UUID.randomUUID();
		player   = mock(Player.class);
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.isOnline()).thenReturn(true);
		when(player.getLocation()).thenReturn(new Location(mock(World.class), 0, 64, 0));
		bukkit.statics().when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

		wanted = new Wanted(mock(JavaPlugin.class), 1, 5); // no owner: setLevel fires no events
		wanted.setLevel(3);

		user = mock(User.class);
		when(user.getWanted()).thenReturn(wanted);
		UserManager<Player> users = mock(UserManager.class);
		when(users.getUser(player)).thenReturn(user);

		WantedSettings wantedSettings = mock(WantedSettings.class);
		when(wantedSettings.getWantedDecreasedMessageTemplate()).thenReturn("%level% %stars%");

		group      = new CopGroup(playerId);
		copManager = mock(CopManager.class);
		service    = new EvasionService(mock(JavaPlugin.class), copManager, users, mock(DetainmentService.class),
		                                wantedSettings);
	}

	@AfterEach
	void tearDown() {
		settings.close();
		bukkit.close();
	}

	@Test
	@DisplayName("handles only a tracked player whose group has had a cop, and only while evasion is enabled")
	void handles_requiresTrackedStaffedAndEnabled() {
		assertFalse(service.handles(null));
		assertFalse(service.handles(player), "untracked");

		service.track(player);
		assertFalse(service.handles(player), "no cop group: the old decay stays the fallback");

		when(copManager.getGroup(playerId)).thenReturn(group);
		assertFalse(service.handles(player), "a group no cop ever joined");

		group.add(mock(CopNpc.class));
		assertTrue(group.isStaffed());
		assertTrue(service.handles(player));

		settings.when(Settings::isWantedEvasionEnabled).thenReturn(false);
		assertFalse(service.handles(player), "Evasion.Enable: false");
	}

	@Test
	@DisplayName("an unseen player drops one star when the clock runs out and keeps searching at the new level")
	void clockRunsOut_dropsOneStar() {
		staffedGroup();
		service.track(player);
		Runnable tick = capturedTick();

		tick.run();
		assertEquals(EvasionState.SEARCHING, service.getState(playerId), "the squad never saw him");
		assertEquals(3, wanted.getLevel());

		tick.run();

		assertEquals(2, wanted.getLevel());
		verify(user).sendMessage("2 " + Wanted.buildStars(2, 5));
		assertEquals(EvasionState.SEARCHING, service.getState(playerId));
		assertEquals(2, service.getSnapshot(playerId).level());
		assertEquals(2D, service.getSnapshot(playerId).remainingSeconds(), "the clock restarted");

		EvasionStateChangeEvent change = firedChanges().get(0);
		assertEquals(EvasionState.NONE, change.getOldState());
		assertEquals(EvasionState.SEARCHING, change.getNewState());
	}

	@Test
	@DisplayName("ALL_STARS drops every star at once")
	void allStars_dropsEverything() {
		settings.when(Settings::getWantedEvasionDropMode).thenReturn("ALL_STARS");
		staffedGroup();
		service.track(player);
		Runnable tick = capturedTick();

		tick.run();
		tick.run();

		assertEquals(0, wanted.getLevel());
		assertFalse(wanted.isWanted());
	}

	@Test
	@DisplayName("without a staffed group the player stays NONE and keeps his stars")
	void noGroup_staysNone() {
		service.track(player);
		Runnable tick = capturedTick();

		tick.run();
		tick.run();
		tick.run();

		assertEquals(EvasionState.NONE, service.getState(playerId));
		assertEquals(3, wanted.getLevel());
		verify(user, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("untrack reports the change to NONE and stops the task once nobody is tracked")
	void untrack_firesNoneAndStopsTask() {
		staffedGroup();
		service.track(player);
		capturedTick().run();

		service.untrack(playerId);

		assertEquals(EvasionState.NONE, service.getState(playerId));
		List<EvasionStateChangeEvent> changes = firedChanges();
		EvasionStateChangeEvent       last    = changes.get(changes.size() - 1);
		assertEquals(EvasionState.SEARCHING, last.getOldState());
		assertEquals(EvasionState.NONE, last.getNewState());
		verify(task).cancel();
	}

	private void staffedGroup() {
		when(copManager.getGroup(playerId)).thenReturn(group);
		group.add(mock(CopNpc.class));
	}

	private Runnable capturedTick() {
		ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
		verify(bukkit.scheduler()).runTaskTimer(any(Plugin.class), captor.capture(), anyLong(), anyLong());
		return captor.getValue();
	}

	private List<EvasionStateChangeEvent> firedChanges() {
		ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		verify(bukkit.pluginManager(), atLeast(0)).callEvent(captor.capture());
		return captor.getAllValues().stream()
		             .filter(EvasionStateChangeEvent.class::isInstance)
		             .map(EvasionStateChangeEvent.class::cast)
		             .toList();
	}
}
