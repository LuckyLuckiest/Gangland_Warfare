package org.luckyraven.gangland.copsncrooks.npc.police;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase H11 (spec 4.9): a {@link CopGroup} owns one Keystone squad. A cop joins it when it is assigned to the group and
 * leaves it when it is released (despawned, killed, invalid); {@link CopNpc#squadFor} shares the squad only for the
 * group's own wanted player, so a retargeted cop or an entity target never feeds the group's last-known position.
 */
@DisplayName("CopGroup - squad membership")
class CopGroupSquadTest {

	@Test
	@DisplayName("an assigned cop joins the squad; a released cop is destroyed and leaves it")
	void addAndRelease_trackSquadMembership() {
		CopGroup group = new CopGroup(UUID.randomUUID());
		CopNpc   cop   = mock(CopNpc.class);

		group.add(cop);

		assertEquals(List.of(cop), group.getSquad().members());
		verify(cop).setGroup(group);

		group.release(cop, mock(NpcMarkManager.class));

		verify(cop).destroy(any());
		assertTrue(group.getSquad().isEmpty());
	}

	@Test
	@DisplayName("squadFor shares the group squad only for the group's wanted player; leaveSquad drops the cop")
	void squadFor_sharesOnlyForTheGroupTarget_andLeaveSquadDropsTheCop() {
		UUID     wantedId = UUID.randomUUID();
		CopGroup group    = new CopGroup(wantedId);
		CopNpc   cop      = mock(CopNpc.class, CALLS_REAL_METHODS); // real squad methods, no Citizens NPC behind it
		cop.setGroup(group);
		group.getSquad().add(cop);

		LivingEntity wanted = mock(LivingEntity.class);
		when(wanted.getUniqueId()).thenReturn(wantedId);

		assertSame(group.getSquad(), cop.squadFor(wanted));

		cop.leaveSquad();

		assertTrue(group.getSquad().isEmpty());
	}

	@Test
	@DisplayName("squadFor gives a non-group target the cop's own squad, seeded at its position; stable until the target changes")
	void squadFor_nonGroupTarget_getsOwnSquadSeededAtItsPosition() {
		UUID     wantedId = UUID.randomUUID();
		CopGroup group    = new CopGroup(wantedId);
		CopNpc   cop      = mock(CopNpc.class, CALLS_REAL_METHODS); // real squad methods, no Citizens NPC behind it
		cop.setGroup(group);
		group.getSquad().add(cop);

		World        world    = mock(World.class);
		LivingEntity other    = mock(LivingEntity.class);
		Location     otherLoc = new Location(world, 5, 64, 5);
		when(other.getUniqueId()).thenReturn(UUID.randomUUID());
		when(other.getLocation()).thenReturn(otherLoc);

		NpcSquad solo = cop.squadFor(other);

		assertNotNull(solo);
		assertEquals(otherLoc, solo.lastKnownLocation());
		assertTrue(group.getSquad().isEmpty());
		assertSame(solo, cop.squadFor(other));

		LivingEntity another    = mock(LivingEntity.class);
		Location     anotherLoc = new Location(world, 9, 70, 9);
		when(another.getUniqueId()).thenReturn(UUID.randomUUID());
		when(another.getLocation()).thenReturn(anotherLoc);

		NpcSquad solo2 = cop.squadFor(another);

		assertNotSame(solo, solo2);
		assertEquals(anotherLoc, solo2.lastKnownLocation());
	}

	@Test
	@DisplayName("release leaves the squad before destroying, even when destroy throws")
	void release_leavesSquadBeforeDestroyThrows() {
		CopGroup       group       = new CopGroup(UUID.randomUUID());
		CopNpc         cop         = mock(CopNpc.class);
		NpcMarkManager markManager = mock(NpcMarkManager.class);
		group.add(cop);

		RuntimeException boom = new RuntimeException("boom");
		doThrow(boom).when(cop).destroy(any());

		RuntimeException thrown = assertThrows(RuntimeException.class, () -> group.release(cop, markManager));

		assertSame(boom, thrown);
		assertTrue(group.getSquad().isEmpty());
	}

	@Test
	@DisplayName("0.12 F4: release counts towards drainLost, from either call site, until it is drained")
	void release_countsTowardsDrainLost() {
		CopGroup       group       = new CopGroup(UUID.randomUUID());
		NpcMarkManager markManager = mock(NpcMarkManager.class);

		CopNpc first  = mock(CopNpc.class);
		CopNpc second = mock(CopNpc.class);
		group.add(first);
		group.add(second);

		assertEquals(0, group.drainLost(), "nothing lost yet");

		group.release(first, markManager);
		group.release(second, markManager);

		assertEquals(2, group.drainLost(), "both release() calls counted, regardless of which loop made them");
		assertEquals(0, group.drainLost(), "drainLost resets the count");
	}

	@Test
	@DisplayName("0.12 F4: resetEpisode clears a reused group's backup bookkeeping for a fresh wanted episode")
	void resetEpisode_clearsBackupBookkeeping() {
		CopGroup group = new CopGroup(UUID.randomUUID());

		group.markInitialResponseDone();
		group.addLosses(3, System.currentTimeMillis() + 60_000L);

		assertTrue(group.isInitialResponseDone());
		assertEquals(3, group.getPendingBackup());

		group.resetEpisode();

		assertFalse(group.isInitialResponseDone(), "the new episode's initial response has not happened yet");
		assertEquals(0, group.getPendingBackup());
		assertFalse(group.isBackupDue(System.currentTimeMillis() + 60_000L));
	}
}
