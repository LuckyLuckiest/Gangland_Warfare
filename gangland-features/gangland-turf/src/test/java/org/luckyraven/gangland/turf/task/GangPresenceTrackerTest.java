package org.luckyraven.gangland.turf.task;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.turf.capture.CaptureRules;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.FakeUserLookup;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the inactivity-release schedule of {@link GangPresenceTracker} (TF-09): the first release sweep runs five
 * minutes after the tracker starts, not a full day, so a server restarted daily still releases an inactive gang's turf.
 * The heartbeat keeps its one-minute cadence. Both tasks are scheduled on the Bukkit scheduler mock from
 * {@link BukkitStatics}. W2-R1 adds the qualifying-member heartbeat ({@code heartbeat(long)}) and the wake-up record.
 */
@DisplayName("GangPresenceTracker - release sweep timing, shutdown (TF-09, TF-07) and qualifying-member heartbeat (W2-R1)")
class GangPresenceTrackerTest {

	private static final long FIRST_RELEASE_TICKS = 6000L;      // 5 minutes
	private static final long RELEASE_PERIOD_TICKS = 1728000L;  // 24 hours

	private static final long   NOW     = 100_000_000L;
	private static final long   MINUTE  = 60_000L;
	private static final long   HOUR    = 3_600_000L;
	private static final long   DAY     = 24 * HOUR;
	private static final int    GANG_ID = 7;
	private static final CaptureRules AGE_24H = new CaptureRules(CaptureRules.Window.OWNERS_ONLINE, 120, 24, 15, 60, 10, 4);
	private static final CaptureRules AGE_0   = new CaptureRules(CaptureRules.Window.OWNERS_ONLINE, 120, 0, 15, 60, 10, 4);

	@Test
	@DisplayName("start: the first release sweep runs after five minutes, then every 24 hours (TF-09)")
	void start_firstReleaseRunsAfterFiveMinutes() {
		JavaPlugin plugin  = mock(JavaPlugin.class);
		Runnable   release = mock(Runnable.class);
		GangPresenceTracker tracker = new GangPresenceTracker(plugin,
		                                                      mock(GangLookupContract.class),
		                                                      mock(UserLookupContract.class),
		                                                      10,
		                                                      release, () -> CaptureRules.LEGACY,
		                                                      CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90));

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			tracker.start();

			verify(bukkit.scheduler()).runTaskTimer(plugin, release, FIRST_RELEASE_TICKS, RELEASE_PERIOD_TICKS);
		}
	}

	@Test
	@DisplayName("onShutdown: cancels the heartbeat and the release task (TF-07)")
	void onShutdown_cancelsBothTasks() {
		JavaPlugin plugin = mock(JavaPlugin.class);
		GangPresenceTracker tracker = new GangPresenceTracker(plugin,
		                                                      mock(GangLookupContract.class),
		                                                      mock(UserLookupContract.class),
		                                                      10,
		                                                      mock(Runnable.class), () -> CaptureRules.LEGACY,
		                                                      CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90));

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			BukkitTask heartbeat = mock(BukkitTask.class);
			BukkitTask release   = mock(BukkitTask.class);
			when(bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
					.thenReturn(heartbeat, release);
			tracker.start();

			tracker.onShutdown();

			verify(heartbeat).cancel();
			verify(release).cancel();
		}
	}

	private FakeGangLookup gangs;
	private FakeUserLookup users;

	private void setUpWorld() {
		gangs = new FakeGangLookup();
		users = new FakeUserLookup();
	}

	private GangPresenceTracker tracker(CaptureRules rules) {
		return new GangPresenceTracker(mock(JavaPlugin.class), gangs, users, 10, mock(Runnable.class),
		                               () -> rules,
		                               CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90));
	}

	/** A gang created {@code createdAgo} ms before NOW and last stamped {@code lastStampedAt} (0 = never). */
	private Gang gang(long createdAgo, long lastStampedAt, Member... members) {
		Gang gang = CaptureFixtures.gang(GANG_ID);
		when(gang.getCreated()).thenReturn(NOW - createdAgo);
		when(gang.getLastMemberOnlineAt()).thenReturn(lastStampedAt);
		when(gang.getMembers()).thenReturn(List.of(members));
		gangs.register(gang);
		return gang;
	}

	/** A member of GANG_ID who joined {@code joinedAgo} ms before NOW (0 = legacy join date of 0). */
	private static Member member(UUID uuid, long joinedAgo) {
		Member member = mock(Member.class);
		when(member.getUuid()).thenReturn(uuid);
		when(member.getGangJoinDateLong()).thenReturn(joinedAgo == 0L ? 0L : NOW - joinedAgo);
		return member;
	}

	/** Puts one online player of GANG_ID on the server (the Bukkit stub is set by the caller). */
	private Player onlineMember(UUID uuid) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);
		users.register(player, CaptureFixtures.userInGang(GANG_ID));
		return player;
	}

	@Test
	@DisplayName("heartbeat: a young member of an old gang does not keep the gang's turfs open")
	void heartbeat_youngMemberOfOldGang_doesNotStamp() {
		setUpWorld();
		UUID uuid = UUID.randomUUID();
		Gang gang = gang(10 * DAY, 0L, member(uuid, 2 * HOUR));
		Player player = onlineMember(uuid);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(Bukkit::getOnlinePlayers).thenAnswer(i -> List.of(player));
			tracker(AGE_24H).heartbeat(NOW);
		}

		verify(gang, never()).setLastMemberOnlineAt(anyLong());
	}

	@Test
	@DisplayName("heartbeat: a member older than the minimum age keeps the gang open")
	void heartbeat_oldMember_stamps() {
		setUpWorld();
		UUID uuid = UUID.randomUUID();
		Gang gang = gang(10 * DAY, 0L, member(uuid, 3 * DAY));
		Player player = onlineMember(uuid);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(Bukkit::getOnlinePlayers).thenAnswer(i -> List.of(player));
			tracker(AGE_24H).heartbeat(NOW);
		}

		verify(gang).setLastMemberOnlineAt(NOW);
	}

	@Test
	@DisplayName("heartbeat: in a young gang every member counts, even a fresh one")
	void heartbeat_youngGang_everyMemberCounts() {
		setUpWorld();
		UUID uuid = UUID.randomUUID();
		Gang gang = gang(2 * HOUR, 0L, member(uuid, HOUR));
		Player player = onlineMember(uuid);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(Bukkit::getOnlinePlayers).thenAnswer(i -> List.of(player));
			tracker(AGE_24H).heartbeat(NOW);
		}

		verify(gang).setLastMemberOnlineAt(NOW);
	}

	@Test
	@DisplayName("heartbeat: a legacy join date of 0 counts as old")
	void heartbeat_legacyJoinDateZero_counts() {
		setUpWorld();
		UUID uuid = UUID.randomUUID();
		Gang gang = gang(10 * DAY, 0L, member(uuid, 0L));
		Player player = onlineMember(uuid);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(Bukkit::getOnlinePlayers).thenAnswer(i -> List.of(player));
			tracker(AGE_24H).heartbeat(NOW);
		}

		verify(gang).setLastMemberOnlineAt(NOW);
	}

	@Test
	@DisplayName("heartbeat: a qualifying member after a closed window wakes the gang")
	void heartbeat_wakesGangWhenWindowWasClosed() {
		setUpWorld();
		UUID uuid = UUID.randomUUID();
		gang(10 * DAY, NOW - 11 * MINUTE, member(uuid, 3 * DAY));
		Player player = onlineMember(uuid);
		GangPresenceTracker tracker = tracker(AGE_24H);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(Bukkit::getOnlinePlayers).thenAnswer(i -> List.of(player));
			tracker.heartbeat(NOW);
		}

		assertEquals(NOW, tracker.wokeAt(GANG_ID));
	}

	@Test
	@DisplayName("heartbeat: a member during an open window does not start a new wake-up")
	void heartbeat_noWakeWhenWindowOpen() {
		setUpWorld();
		UUID uuid = UUID.randomUUID();
		gang(10 * DAY, NOW - 3 * MINUTE, member(uuid, 3 * DAY));
		Player player = onlineMember(uuid);
		GangPresenceTracker tracker = tracker(AGE_24H);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(Bukkit::getOnlinePlayers).thenAnswer(i -> List.of(player));
			tracker.heartbeat(NOW);
		}

		assertEquals(0L, tracker.wokeAt(GANG_ID));
	}

	@Test
	@DisplayName("heartbeat: a minimum age of 0 counts everyone")
	void ageZero_countsEveryone() {
		setUpWorld();
		UUID uuid = UUID.randomUUID();
		Gang gang = gang(10 * DAY, 0L, member(uuid, MINUTE));
		Player player = onlineMember(uuid);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(Bukkit::getOnlinePlayers).thenAnswer(i -> List.of(player));
			tracker(AGE_0).heartbeat(NOW);
		}

		verify(gang).setLastMemberOnlineAt(NOW);
	}
}
