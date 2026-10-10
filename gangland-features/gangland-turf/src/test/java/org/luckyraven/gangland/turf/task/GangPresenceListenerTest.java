package org.luckyraven.gangland.turf.task;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.gang.GangMembershipView;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.member.Member;

import org.luckyraven.gangland.turf.capture.CaptureRules;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.FakeUserLookup;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W2-R1 join and quit edges: a qualifying member's login after a closed window wakes the gang and sends its owner one
 * camping line per owned turf with rivals inside; quit stamps only when the last qualifying member leaves. The
 * listener is constructed directly with fakes (it is a {@code @ListenerHandler}, so the framework injects it in
 * production).
 */
@DisplayName("GangPresenceListener - qualifying join wakes the gang, camping lines, quit stamps (W2-R1)")
class GangPresenceListenerTest {

	private static final long NOW    = System.currentTimeMillis();   // the listener reads the wall clock
	private static final long HOUR   = 3_600_000L;
	private static final long DAY    = 24 * HOUR;
	private static final int  OWNER  = 7;
	private static final int  RIVAL  = 9;

	private final FakeGangLookup gangs = new FakeGangLookup();
	private final FakeUserLookup users = new FakeUserLookup();
	private final GangMembership membership = new GangMembership();
	private final GangMembershipView view = mock(GangMembershipView.class);
	private final CaptureRules   rules = new CaptureRules(CaptureRules.Window.OWNERS_ONLINE, 120, 24, 15, 60, 10, 4);

	private GangPresenceTracker tracker;
	private TurfManager         turfs;
	private TurfMessageContract legacy;
	private GangPresenceListener listener;

	private Gang  owner;
	private Gang  rival;
	private Turf  docks;
	private Turf  quay;

	private MockedStatic<Settings> staticSettings;

	void setUp(long ownerLastStamp) throws IOException {
		staticSettings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		staticSettings.when(Settings::getMoneySymbol).thenReturn("$");
		when(view.gangIdOf(any(UUID.class))).thenReturn(-1);
		membership.install(view);

		owner = CaptureFixtures.gang(OWNER);
		when(owner.getName()).thenReturn("Blues");
		when(owner.getCreated()).thenReturn(NOW - 10 * DAY);
		when(owner.getLastMemberOnlineAt()).thenReturn(ownerLastStamp);
		gangs.register(owner);

		rival = CaptureFixtures.gang(RIVAL);
		when(rival.getName()).thenReturn("Rats");
		gangs.register(rival);

		var settings = CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90);
		tracker = new GangPresenceTracker(mock(org.bukkit.plugin.java.JavaPlugin.class), gangs, users, 10,
		                                  mock(Runnable.class), () -> rules, settings);

		docks = new Turf(1, "Docks", new CuboidRegion("world", 0, 0, 10, 10), OWNER, BigDecimal.TEN, 0L, 0L);
		quay  = new Turf(2, "Quay", new CuboidRegion("world", 20, 20, 30, 30), OWNER, BigDecimal.TEN, 0L, 0L);
		turfs = mock(TurfManager.class);
		when(turfs.getAll()).thenReturn(List.of(docks, quay));

		TurfRuntimeState dockState = new TurfRuntimeState(1);
		dockState.setChallengersInside(Map.of(RIVAL, 2));
		when(turfs.getRuntimeState(1)).thenReturn(dockState);

		legacy = mock(TurfMessageContract.class);
		when(legacy.formatDuration(anyLong())).thenReturn("2:00");
		TurfMessages text = new TurfMessages(
				YamlFixture.fileManager(tempDir, "turf_messages", YamlFixture.shipped("/turf/turf_messages.yml")),
				legacy, settings);

		listener = new GangPresenceListener(gangs, membership, tracker, turfs, text, legacy);
	}

	@org.junit.jupiter.api.io.TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.NEVER)
	Path tempDir;

	@org.junit.jupiter.api.AfterEach
	void releaseStatics() {
		if (staticSettings != null) {
			staticSettings.close();
		}
	}

	private static Member member(UUID uuid, long joinedAgo) {
		Member member = mock(Member.class);
		when(member.getUuid()).thenReturn(uuid);
		when(member.getGangJoinDateLong()).thenReturn(NOW - joinedAgo);
		return member;
	}

	/** A player of the owner gang, registered with the given member record. */
	private Player ownerPlayer(Member member) {
		Player player = mock(Player.class);
		UUID uuid = member.getUuid();
		when(player.getUniqueId()).thenReturn(uuid);
		when(view.gangIdOf(uuid)).thenReturn(OWNER);
		when(owner.getMembers()).thenReturn(new java.util.ArrayList<>(List.of(member)));
		return player;
	}

	private PlayerJoinEvent join(Player player) {
		PlayerJoinEvent event = mock(PlayerJoinEvent.class);
		when(event.getPlayer()).thenReturn(player);
		return event;
	}

	private PlayerQuitEvent quit(Player player) {
		PlayerQuitEvent event = mock(PlayerQuitEvent.class);
		when(event.getPlayer()).thenReturn(player);
		return event;
	}

	@Test
	@DisplayName("join: a qualifying member after a closed window wakes the gang and stamps it")
	void join_qualifyingMember_closedWindow_recordsWakeUp_andStamps() throws IOException {
		setUp(0L);
		Player player = ownerPlayer(member(UUID.randomUUID(), 3 * DAY));

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			listener.onJoin(join(player));
		}

		verify(owner).setLastMemberOnlineAt(anyLong());
		assertTrue(tracker.wokeAt(OWNER) > 0L);
	}

	@Test
	@DisplayName("join: a young member of an old gang neither stamps nor wakes the gang")
	void join_youngMember_oldGang_neitherStampsNorWakes() throws IOException {
		setUp(0L);
		Player player = ownerPlayer(member(UUID.randomUUID(), 2 * HOUR));

		listener.onJoin(join(player));

		verify(owner, never()).setLastMemberOnlineAt(anyLong());
		assertEquals(0L, tracker.wokeAt(OWNER));
	}

	@Test
	@DisplayName("join: the wake-up sends one camping line to the joiner per owned turf with rivals inside")
	void join_withCampers_sendsOneCampingLinePerTurf() throws IOException {
		setUp(0L);
		Player player = ownerPlayer(member(UUID.randomUUID(), 3 * DAY));

		listener.onJoin(join(player));

		verify(player, times(1)).sendMessage(anyString());
		var line = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(player).sendMessage(line.capture());
		assertTrue(line.getValue().contains("Rats") && line.getValue().contains("Docks"), line.getValue());
		assertTrue(line.getValue().contains("2:00"), line.getValue());
	}

	@Test
	@DisplayName("join: an open window neither wakes the gang nor sends camping lines")
	void join_openWindow_noWakeNoCampingLine() throws IOException {
		setUp(NOW - 60_000L);
		Player player = ownerPlayer(member(UUID.randomUUID(), 3 * DAY));

		listener.onJoin(join(player));

		assertEquals(0L, tracker.wokeAt(OWNER));
		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("quit: the last qualifying member leaving stamps the gang")
	void quit_lastQualifyingMember_stamps() throws IOException {
		setUp(NOW - 60_000L);
		Player player = ownerPlayer(member(UUID.randomUUID(), 3 * DAY));

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(Bukkit::getOnlinePlayers).thenAnswer(i -> List.of(player));
			listener.onQuit(quit(player));
		}

		verify(owner).setLastMemberOnlineAt(anyLong());
	}

	@Test
	@DisplayName("quit: another qualifying member still online means no stamp")
	void quit_otherQualifyingMemberOnline_doesNotStamp() throws IOException {
		setUp(NOW - 60_000L);
		UUID leaverId = UUID.randomUUID();
		UUID stayerId = UUID.randomUUID();
		Player leaver = mock(Player.class);
		when(leaver.getUniqueId()).thenReturn(leaverId);
		when(view.gangIdOf(leaverId)).thenReturn(OWNER);
		Player stayer = mock(Player.class);
		when(stayer.getUniqueId()).thenReturn(stayerId);
		when(view.gangIdOf(stayerId)).thenReturn(OWNER);
		Member leaverMember = member(leaverId, 3 * DAY);
		Member stayerMember = member(stayerId, 3 * DAY);
		when(owner.getMembers()).thenReturn(List.of(leaverMember, stayerMember));

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(Bukkit::getOnlinePlayers).thenAnswer(i -> List.of(leaver, stayer));
			listener.onQuit(quit(leaver));
		}

		verify(owner, never()).setLastMemberOnlineAt(anyLong());
	}

	@Test
	@DisplayName("join: an unhydrated user record still wakes the gang and sends the camping line")
	void join_unhydratedUser_stillWakesAndSendsCampingLine() throws IOException {
		setUp(0L);
		UUID uuid = UUID.randomUUID();
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);
		Member member = member(uuid, 3 * DAY);
		when(owner.getMembers()).thenReturn(new java.util.ArrayList<>(List.of(member)));
		when(view.gangIdOf(uuid)).thenReturn(OWNER);
		// no users.register: at join the user record is gang-less (its gang id is hydrated later, async)

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			listener.onJoin(join(player));
		}

		assertTrue(tracker.wokeAt(OWNER) > 0L);
		verify(owner).setLastMemberOnlineAt(anyLong());
		verify(player, times(1)).sendMessage(anyString());
	}
}