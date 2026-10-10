package org.luckyraven.gangland.turf.listener;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.capture.CaptureSettings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.contract.TurfRepositoryContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.events.TurfCaptureFailedEvent;
import org.luckyraven.gangland.turf.events.TurfCapturedEvent;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.log.TurfEventBuffer;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.FakeUserLookup;
import org.luckyraven.gangland.turf.support.TurfTextFixture;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pins the while-you-were-away digest (TF-37): the lines recorded from capture, failure and owner-change events, and
 * the join digest (income since the last quit, then up to five event lines, then the map link).
 */
@DisplayName("TurfDigestListener - records turf events and replays them on join (TF-37)")
class TurfDigestListenerTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private TurfEventBuffer        buffer;
	private FakeGangLookup         gangs;
	private FakeUserLookup         users;
	private TurfMessages           text;
	private CaptureSettings        capture;
	private MockedStatic<Settings> settings;
	private TurfDigestListener     listener;
	private final AtomicLong       clock = new AtomicLong(1_000_000L);

	@BeforeEach
	void setUp() throws Exception {
		buffer   = new TurfEventBuffer();
		TurfManager turfs = new TurfManager(mock(TurfRepositoryContract.class));
		gangs    = new FakeGangLookup();
		users    = new FakeUserLookup();
		text     = TurfTextFixture.messages(tempDir, mock(TurfMessageContract.class));
		capture  = CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90);
		settings = TurfTextFixture.stubMoneySymbol();
		settings.when(() -> Settings.formatAmount(any(BigDecimal.class)))
				.thenAnswer(invocation -> ((BigDecimal) invocation.getArgument(0)).toPlainString());
		listener = new TurfDigestListener(buffer, turfs, gangs, users, text, capture, mock(JavaPlugin.class));
		listener.setClock(clock::get);
	}

	@AfterEach
	void tearDown() {
		settings.close();
	}

	private static Gang named(int id, String name) {
		Gang gang = CaptureFixtures.gang(id);
		when(gang.getName()).thenReturn(name);
		return gang;
	}

	private static Turf docks(int owner) {
		return new Turf(1, "Docks", new CuboidRegion("world", 0, 0, 10, 10), owner, BigDecimal.TEN, 0L, 0L);
	}

	private static String plain(String line) {
		return ChatColor.stripColor(line);
	}

	@Test
	@DisplayName("capture_recordsWinnerAndLoserLines: the winner gets Captured, the loser gets Lost")
	void capture_recordsWinnerAndLoserLines() {
		Gang vipers = named(1, "Vipers");
		Gang rats   = named(2, "Rats");
		gangs.register(vipers);
		gangs.register(rats);

		listener.onCaptured(new TurfCapturedEvent(docks(2), vipers, rats));

		assertEquals("Captured Docks from Vipers", plain(buffer.latest(2, 10).get(0).line()));
		assertEquals("Lost Docks to Rats", plain(buffer.latest(1, 10).get(0).line()));
	}

	@Test
	@DisplayName("stalled_recordsBothSides: a stalled attack is recorded for the defenders and the attackers")
	void stalled_recordsBothSides() {
		gangs.register(named(1, "Vipers"));
		gangs.register(named(2, "Rats"));

		listener.onCaptureFailed(new TurfCaptureFailedEvent(docks(1), TurfCaptureFailedEvent.Reason.STALLED, 2));

		assertEquals("Attack on Docks stalled", plain(buffer.latest(1, 10).get(0).line()));
		assertEquals("Attack on Docks stalled", plain(buffer.latest(2, 10).get(0).line()));
	}

	@Test
	@DisplayName("defended_recordsHeldForOwner: a defended turf is recorded as Held against the attackers")
	void defended_recordsHeldForOwner() {
		gangs.register(named(1, "Vipers"));
		gangs.register(named(2, "Rats"));

		listener.onCaptureFailed(new TurfCaptureFailedEvent(docks(1), TurfCaptureFailedEvent.Reason.DEFENDED, 2));

		assertEquals("Held Docks against Rats", plain(buffer.latest(1, 10).get(0).line()));
	}

	@Test
	@DisplayName("T-271: a turf defended twice within the quiet window is recorded once")
	void defendedTwice_recordedOnce() {
		gangs.register(named(1, "Vipers"));
		gangs.register(named(2, "Rats"));

		listener.onCaptureFailed(new TurfCaptureFailedEvent(docks(1), TurfCaptureFailedEvent.Reason.DEFENDED, 2));
		clock.addAndGet(10 * 60_000L);
		listener.onCaptureFailed(new TurfCaptureFailedEvent(docks(1), TurfCaptureFailedEvent.Reason.DEFENDED, 2));

		assertEquals(1, buffer.latest(1, 10).size());
	}

	@Test
	@DisplayName("T-271: a turf defended again after the quiet window is recorded again")
	void defendedAfterQuietWindow_recordedAgain() {
		gangs.register(named(1, "Vipers"));
		gangs.register(named(2, "Rats"));

		listener.onCaptureFailed(new TurfCaptureFailedEvent(docks(1), TurfCaptureFailedEvent.Reason.DEFENDED, 2));
		clock.addAndGet(16 * 60_000L);
		listener.onCaptureFailed(new TurfCaptureFailedEvent(docks(1), TurfCaptureFailedEvent.Reason.DEFENDED, 2));

		assertEquals(2, buffer.latest(1, 10).size());
	}

	@Test
	@DisplayName("inactivityRelease_recordsLostLine: an inactivity release is recorded as Lost with the day limit")
	void inactivityRelease_recordsLostLine() {
		gangs.register(named(1, "Vipers"));

		listener.onOwnerChanged(new TurfOwnerChangedEvent(docks(1), 1, null, TurfOwnerChangedEvent.Cause.INACTIVITY));

		assertEquals("Lost Docks (no owner online for 10 days)", plain(buffer.latest(1, 10).get(0).line()));
	}

	/** A player whose user is in {@code gangId}; the gang itself is registered by the test. */
	private Player member(int gangId) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.spigot()).thenReturn(mock(Player.Spigot.class));
		@SuppressWarnings("unchecked")
		User<Player> user = mock(User.class);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(gangId);
		users.register(player, user);
		return player;
	}

	@Test
	@DisplayName("join_afterQuit_showsIncomeThenEventsSinceQuit_max5: income, the two events after the quit, then the map link")
	void join_afterQuit_showsIncomeThenEventsSinceQuit_max5() {
		gangs.register(named(1, "Vipers"));
		Player player = member(1);
		buffer.markQuit(player.getUniqueId(), 10L);
		buffer.record(1, 20L, "event one");
		buffer.record(1, 30L, "event two");
		buffer.recordIncome(1, 25L, new BigDecimal("400"));

		listener.sendDigest(player);

		ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
		verify(player, times(3)).sendMessage(lines.capture());
		List<String> sent = lines.getAllValues().stream().map(TurfDigestListenerTest::plain).toList();
		assertEquals("Income earned while you were away: $400", sent.get(0));
		assertEquals("event one", sent.get(1));
		assertEquals("event two", sent.get(2));
		verify(player.spigot(), times(1)).sendMessage(any(BaseComponent[].class));
	}

	@Test
	@DisplayName("join_afterRestart_showsLastThree: with no quit stamp the last three event lines show, and no income line")
	void join_afterRestart_showsLastThree() {
		gangs.register(named(1, "Vipers"));
		Player player = member(1);
		for (int at = 1; at <= 5; at++) {
			buffer.record(1, at, "event " + at);
		}
		buffer.recordIncome(1, 2L, new BigDecimal("400"));

		listener.sendDigest(player);

		ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
		verify(player, times(3)).sendMessage(lines.capture());
		assertEquals(List.of("event 3", "event 4", "event 5"),
		             lines.getAllValues().stream().map(TurfDigestListenerTest::plain).toList());
		verify(player.spigot(), times(1)).sendMessage(any(BaseComponent[].class));
	}

	@Test
	@DisplayName("join_afterQuit_capsAtFiveLines: income plus the four newest events since the quit, then the map link")
	void join_afterQuit_capsAtFiveLines() {
		gangs.register(named(1, "Vipers"));
		Player player = member(1);
		buffer.markQuit(player.getUniqueId(), 10L);
		buffer.recordIncome(1, 25L, new BigDecimal("400"));
		for (int at = 11; at <= 16; at++) {
			buffer.record(1, at, "event " + at);
		}

		listener.sendDigest(player);

		ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
		verify(player, times(5)).sendMessage(lines.capture());
		assertEquals(List.of("Income earned while you were away: $400", "event 13", "event 14", "event 15", "event 16"),
		             lines.getAllValues().stream().map(TurfDigestListenerTest::plain).toList());
		verify(player.spigot(), times(1)).sendMessage(any(BaseComponent[].class));
	}

	@Test
	@DisplayName("join_gangless_getsNothing: a player with no gang gets no digest and no map link")
	void join_gangless_getsNothing() {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		@SuppressWarnings("unchecked")
		User<Player> user = mock(User.class);
		when(user.hasGang()).thenReturn(false);
		users.register(player, user);
		buffer.record(1, 1L, "event");

		listener.sendDigest(player);

		verify(player, never()).sendMessage(anyString());
		verify(player, never()).spigot();
	}
}
