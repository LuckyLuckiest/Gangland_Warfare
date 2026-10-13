package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.place.AdminRegionRegistry;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.TrespassSettings;
import org.luckyraven.gangland.copsncrooks.wanted.trespass.TrespassTracker;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.keystone.persistence.repository.IRepository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P-CRIME-4 (0.20.0): walking or teleporting into an admin region tagged {@code restricted} commits
 * {@code Trespass_Restricted} as seen by a cop, once per entry and at most once per region per
 * {@code Wanted.Trespass.Repeat_Seconds}; restrained, bypass, creative and spectator players and a disabled
 * {@code Wanted.Trespass.Enable} are exempt. Uses a real {@link AdminRegionRegistry} and {@link TrespassTracker}.
 */
@DisplayName("TrespassListener")
class TrespassListenerTest {

	private static final String MESSAGE = "&4Restricted area! The police are on their way.";

	private final World             world      = mock(World.class);
	private final Player            player     = mock(Player.class);
	private final CrimeService      crimes     = mock(CrimeService.class);
	private final DetainmentService detainment = mock(DetainmentService.class);
	private final ChaseConfigLoader config     = mock(ChaseConfigLoader.class);
	private final WantedMessages    messages   = mock(WantedMessages.class);
	private final AtomicLong        clock      = new AtomicLong(1_000_000L);

	private AdminRegionRegistry regions;
	private TrespassListener    listener;

	// the restricted vault spans x 10..20; the den (a hideout) x 30..40; everything else is open ground
	private Location outside;
	private Location inside;
	private Location inside2;
	private Location hideout;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		when(world.getName()).thenReturn("world");
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getName()).thenReturn("Alice");
		when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
		when(player.isOnline()).thenReturn(true);
		when(config.getTrespass()).thenReturn(TrespassSettings.DEFAULT);
		when(messages.format(any(WantedMessages.Key.class), any())).thenReturn(MESSAGE);

		outside = new Location(world, 0, 64, 0);
		inside  = new Location(world, 15, 64, 0);
		inside2 = new Location(world, 16, 64, 0);
		hideout = new Location(world, 35, 64, 0);

		regions = new AdminRegionRegistry(mock(IRepository.class));
		regions.create("vault", new Location(world, 10, 0, -5), new Location(world, 20, 255, 5), "restricted");
		regions.create("den", new Location(world, 30, 0, -5), new Location(world, 40, 255, 5), "hideout");

		listener = new TrespassListener(regions, new TrespassTracker(), detainment, crimes, config, messages,
		                                clock::get);
	}

	private void move(Location from, Location to) {
		listener.onMove(new PlayerMoveEvent(player, from, to));
	}

	private void verifyNoCrime() {
		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("entering a restricted region commits a cop-seen Trespass_Restricted at the entry point and warns")
	void enteringRestrictedRegionCommitsSeenTrespass() {
		move(outside, inside);

		verify(crimes, times(1)).commit(player, Crimes.TRESPASS_RESTRICTED, inside, true, 0);
		verify(player).sendMessage(MESSAGE);
	}

	@Test
	@DisplayName("the warning is the Trespass line of the wanted messages")
	void warningUsesTrespassKey() {
		move(outside, inside);

		verify(messages).format(WantedMessages.Key.TRESPASS, Map.of());
	}

	@Test
	@DisplayName("moving about inside the region does not commit again")
	void standingInsideDoesNotRepeat() {
		move(outside, inside);
		move(inside, inside2);
		move(inside2, inside);

		verify(crimes, times(1)).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("leaving and re-entering at +30 s is ignored, at +61 s it commits again")
	void reentryWithinRepeatSecondsIgnored() {
		move(outside, inside);
		move(inside, outside);
		clock.addAndGet(30_000L);
		move(outside, inside);
		verify(crimes, times(1)).commit(any(), anyString(), any(), anyBoolean(), anyInt());

		move(inside, outside);
		clock.addAndGet(31_000L);
		move(outside, inside);

		verify(crimes, times(2)).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("Repeat_Seconds comes from the config")
	void repeatSecondsFromConfig() {
		when(config.getTrespass()).thenReturn(new TrespassSettings(true, 5));

		move(outside, inside);
		move(inside, outside);
		clock.addAndGet(6_000L);
		move(outside, inside);

		verify(crimes, times(2)).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("a player the detainment service says is cuffed or jailed commits nothing and sees no warning")
	void detainedPlayerExempt() {
		when(detainment.isRestrained(player)).thenReturn(true);

		move(outside, inside);

		verifyNoCrime();
		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a prisoner teleported into a restricted jail gets no stars")
	void prisonerMovedIntoRestrictedJailGetsNothing() {
		when(detainment.isRestrained(player)).thenReturn(true);

		listener.onTeleport(new PlayerTeleportEvent(player, outside, inside, TeleportCause.PLUGIN));

		verifyNoCrime();
	}

	@Test
	@DisplayName("a player released while standing inside is not charged for the entry they were exempt for")
	void releasedInsideDoesNotCommit() {
		when(detainment.isRestrained(player)).thenReturn(true);
		move(outside, inside);

		when(detainment.isRestrained(player)).thenReturn(false);
		move(inside, inside2);

		verifyNoCrime();
	}

	@Test
	@DisplayName("holding gangland.cop.trespass.bypass commits nothing")
	void bypassPermissionExempt() {
		when(player.hasPermission("gangland.cop.trespass.bypass")).thenReturn(true);

		move(outside, inside);

		verifyNoCrime();
	}

	@Test
	@DisplayName("creative and spectator players commit nothing")
	void creativeAndSpectatorExempt() {
		when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
		move(outside, inside);
		move(inside, outside);

		when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
		clock.addAndGet(120_000L);
		move(outside, inside);

		verifyNoCrime();
	}

	@Test
	@DisplayName("Wanted.Trespass.Enable false commits nothing")
	void disabledInConfig() {
		when(config.getTrespass()).thenReturn(new TrespassSettings(false, 60));

		move(outside, inside);

		verifyNoCrime();
	}

	@Test
	@DisplayName("a region without the restricted tag (a hideout) is ignored")
	void nonRestrictedRegionIgnored() {
		move(outside, hideout);

		verifyNoCrime();
	}

	@Test
	@DisplayName("a look-around inside one block (same x, y, z) is not an entry")
	void sameBlockMoveIgnored() {
		move(new Location(world, 15.2, 64, 0.1, 0f, 0f), new Location(world, 15.8, 64, 0.9, 90f, 10f));

		verifyNoCrime();
	}

	@Test
	@DisplayName("a move with a destination in no world is ignored")
	void nullWorldIgnored() {
		move(outside, new Location(null, 15, 64, 0));

		verifyNoCrime();
	}

	@Test
	@DisplayName("a teleport into the region commits the same way")
	void teleportIntoRegionCommits() {
		listener.onTeleport(new PlayerTeleportEvent(player, outside, inside, TeleportCause.COMMAND));

		verify(crimes).commit(player, Crimes.TRESPASS_RESTRICTED, inside, true, 0);
	}

	@Test
	@DisplayName("entering two overlapping restricted regions at once commits once per region")
	void overlappingRegionsCommitOncePerRegion() {
		regions.create("annex", new Location(world, 12, 0, -5), new Location(world, 18, 255, 5), "restricted");

		move(outside, inside);

		verify(crimes, times(2)).commit(player, Crimes.TRESPASS_RESTRICTED, inside, true, 0);
	}

	@Test
	@DisplayName("a quit forgets where the player stood: a rejoin inside is an entry, throttled by Repeat_Seconds")
	void quitForgetsInsideSet() {
		move(outside, inside);
		listener.onQuit(new PlayerQuitEvent(player, "bye"));
		clock.addAndGet(61_000L);

		move(inside, inside2);

		verify(crimes, times(2)).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("the move and teleport handlers skip cancelled events")
	void handlersIgnoreCancelled() throws Exception {
		EventHandler move     = TrespassListener.class.getMethod("onMove", PlayerMoveEvent.class)
		                                              .getAnnotation(EventHandler.class);
		EventHandler teleport = TrespassListener.class.getMethod("onTeleport", PlayerTeleportEvent.class)
		                                              .getAnnotation(EventHandler.class);

		assertTrue(move.ignoreCancelled());
		assertTrue(teleport.ignoreCancelled());
	}
}
