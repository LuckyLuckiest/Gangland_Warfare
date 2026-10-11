package org.luckyraven.gangland.gang.purge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.events.gangs.GangMemberLeftEvent;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.membership.GangDisbandService;
import org.luckyraven.gangland.gang.membership.GangMembershipService;
import org.luckyraven.gangland.gang.membership.GangMembershipService.Result;
import org.luckyraven.gangland.gang.membership.MembershipQueries;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Inactive gangs are disbanded through the disband service; off by default; a fake clock and a last-played map. */
@DisplayName("GangPurgeTask - inactive gang purge")
class GangPurgeTaskTest {

	private static final Instant BOOT = Instant.parse("2026-01-01T00:00:00Z");

	private final Map<UUID, Long> played = new HashMap<>();
	private final long            now    = BOOT.toEpochMilli();

	private GangRules             rules;
	private GangDisbandService    disband;
	private GangMembershipService membership;
	private GangPurgeTask         task;
	private Gang                  dead;
	private Gang                  alive;
	private UUID                  deadOwner;
	private UUID                  aliveOwner;
	private UUID                  aliveRecruit;

	@BeforeEach
	void setUp() {
		GangManager gangManager = mock(GangManager.class);
		rules      = mock(GangRules.class);
		disband    = mock(GangDisbandService.class);
		membership = mock(GangMembershipService.class);
		when(rules.getPurgeInactiveDays()).thenReturn(30);
		when(rules.getPurgeMemberInactiveDays()).thenReturn(60);
		when(rules.getPurgeFirstRunDelayHours()).thenReturn(24);
		when(disband.disband(any(), any(), anyBoolean(), anyBoolean())).thenReturn(Result.success());
		when(membership.leave(any(), any())).thenReturn(Result.success());

		dead  = new Gang(1);
		dead.setName("Ghosts");
		alive = new Gang(2);
		alive.setName("Vipers");
		deadOwner    = add(dead, days(-40));
		aliveOwner   = add(alive, days(-45));
		aliveRecruit = add(alive, days(-1));   // plays yesterday: keeps the gang alive although the owner is gone
		when(gangManager.getAll()).thenAnswer(i -> List.of(dead, alive));
		when(gangManager.getGang(1)).thenReturn(dead);
		when(gangManager.getGang(2)).thenReturn(alive);

		MembershipQueries queries = mock(MembershipQueries.class);
		when(queries.ownerOf(1)).thenReturn(Optional.of(deadOwner));
		when(queries.ownerOf(2)).thenReturn(Optional.of(aliveOwner));

		task = new GangPurgeTask(null, gangManager, queries, rules, disband, membership,
		                         uuid -> played.getOrDefault(uuid, 0L), () -> now);
	}

	@Test
	@DisplayName("offByDefault")
	void offByDefault() {
		Instant later = BOOT.plus(Duration.ofDays(10));

		assertEquals(0, task.tick(later));
		verifyNoInteractions(disband);
	}

	@Test
	@DisplayName("preview_lists")
	void preview_lists() {
		List<PurgeCandidate> candidates = task.preview(Instant.ofEpochMilli(now));

		assertEquals(1, candidates.size());
		assertEquals(1, candidates.get(0).gangId());
		assertEquals("Ghosts", candidates.get(0).name());
		verifyNoInteractions(disband);
		verifyNoInteractions(membership);
	}

	@Test
	@DisplayName("run_disbands")
	void run_disbands() {
		when(rules.isPurgeRefundFee()).thenReturn(true);

		assertEquals(1, task.run(Instant.ofEpochMilli(now)));

		verify(disband).disband(dead, null, true, true);
		verify(disband, never()).disband(eq(alive), any(), anyBoolean(), anyBoolean());
	}

	@Test
	@DisplayName("run_dropsInactiveNonOwnerMembers")
	void run_dropsInactiveNonOwnerMembers() {
		UUID stale = add(alive, days(-61));

		task.run(Instant.ofEpochMilli(now));

		verify(membership).leave(stale, GangMemberLeftEvent.Reason.PURGED);
		verify(membership, never()).leave(eq(aliveOwner), any());
		verify(membership, never()).leave(eq(aliveRecruit), any());
	}

	@Test
	@DisplayName("ownersLastPlayedCounts")
	void ownersLastPlayedCounts() {
		// the owner played yesterday: the gang is not a candidate although nobody else was seen
		played.put(deadOwner, days(-1));

		assertTrue(task.preview(Instant.ofEpochMilli(now)).isEmpty());
	}

	@Test
	@DisplayName("firstRunDelay")
	void firstRunDelay() {
		when(rules.isPurgeEnabled()).thenReturn(true);

		assertEquals(0, task.tick(BOOT.plus(Duration.ofHours(23))), "inside the delay");
		verifyNoInteractions(disband);

		Instant afterDelay = BOOT.plus(Duration.ofHours(25));
		assertEquals(1, task.tick(afterDelay));
		verify(disband, times(1)).disband(eq(dead), any(), anyBoolean(), anyBoolean());

		assertEquals(0, task.tick(afterDelay.plus(Duration.ofHours(2))), "once a day");
	}

	private long days(int offset) {
		return now + Duration.ofDays(offset).toMillis();
	}

	private UUID add(Gang gang, long lastPlayed) {
		Member member = new Member(UUID.randomUUID());
		gang.addMember(member);
		played.put(member.getUuid(), lastPlayed);
		return member.getUuid();
	}

}
