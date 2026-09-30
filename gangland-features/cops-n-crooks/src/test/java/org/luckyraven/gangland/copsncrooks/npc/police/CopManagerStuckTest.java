package org.luckyraven.gangland.copsncrooks.npc.police;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.StuckSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.keystone.npc.NpcSquadSignal;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase H13 (B2): a cop that has been stranded ({@code millisUnreachable()}) for {@code Cops.Stuck.Recycle_Seconds}
 * while the suspect cannot see it is despawned and replaced in the same spawn run, away from the spawner it came from.
 * "Can see" is a view cone plus line of sight within {@code Visibility_Check_Distance} (the bystanders' radius), not a
 * bare ray: a cop on a ledge above the suspect's head is out of view even though the ray is open. Past twice
 * {@code Recycle_Seconds} only a cop in view within 24 blocks is kept.
 */
@DisplayName("CopManager - recycling stranded cops out of view")
class CopManagerStuckTest {

	private CopManagerFixture fx;
	private CopManager        manager;
	private Player            player;
	private UUID              playerId;
	private Wanted            wanted;
	private CopGroup          group;
	private Location          spawner;

	@BeforeEach
	void setUp() {
		fx       = new CopManagerFixture();
		manager  = fx.manager;
		player   = fx.player(10, 10);
		playerId = player.getUniqueId();
		wanted   = CopManagerFixture.wanted(2);
		spawner  = new Location(fx.world, -138.5, 115, 123.5);
		// the suspect looks along +Z (yaw 0), level
		when(player.getEyeLocation()).thenReturn(new Location(fx.world, 10, 65.62, 10, 0f, 0f));
		when(player.hasLineOfSight(any())).thenReturn(true);

		manager.onWantedStart(player, wanted);
		group = manager.groupFor(playerId);
	}

	@AfterEach
	void tearDown() {
		fx.close();
	}

	/** A cop of the group hunting the suspect, standing at (x, y, z), from {@link #spawner}, stranded for {@code ms}. */
	private CopNpc stranded(CopState state, double x, double y, double z, long ms) {
		CopNpc cop = fx.cop(state, x, z);
		LivingEntity body = cop.getEntity();
		when(body.getLocation()).thenReturn(new Location(fx.world, x, y, z));
		when(cop.getSpawnLocation()).thenReturn(spawner);
		when(cop.millisUnreachable()).thenReturn(ms);
		cop.setTargetPlayerId(playerId);
		group.add(cop);
		return cop;
	}

	@Test
	@DisplayName("a stranded cop behind the suspect is released and replaced in the same run, avoiding its spawner")
	void strandedOutOfView_recycledAndReplaced() {
		CopNpc stuck = stranded(CopState.PURSUING, 10, 64, 0, 13_000); // behind him
		CopNpc fine  = stranded(CopState.PURSUING, 10, 64, 20, 0);

		manager.spawnTick(playerId, wanted);

		assertFalse(group.getCops().contains(stuck));
		assertTrue(group.getCops().contains(fine));
		assertEquals(2, group.getCops().size(), "refilled to the wanted-level count, never above it");
		assertFalse(group.getSquad().members().contains(stuck));
		verify(stuck).destroy(any());

		@SuppressWarnings("unchecked") ArgumentCaptor<Predicate<Location>> allowed = ArgumentCaptor.forClass(Predicate.class);
		verify(fx.spawner, atLeastOnce()).spawnNearPlayer(eq(player), anyInt(), allowed.capture());
		assertFalse(allowed.getValue().test(spawner.clone()), "the recycled cop's spawner is avoided");
		assertTrue(allowed.getValue().test(new Location(fx.world, -149.8, 80, 155.7)));
	}

	@Test
	@DisplayName("a stranded cop on a ledge above the suspect's head is recycled although the ray to it is open")
	void strandedAboveHead_outOfCone_recycled() {
		CopNpc stuck = stranded(CopState.PURSUING, 10, 80, 12, 13_000);

		manager.spawnTick(playerId, wanted);

		assertFalse(group.getCops().contains(stuck));
	}

	@Test
	@DisplayName("a stranded cop the suspect is looking at is never recycled, however long")
	void strandedInView_kept() {
		CopNpc stuck = stranded(CopState.PURSUING, 10, 66, 30, 600_000);

		manager.spawnTick(playerId, wanted);

		assertTrue(group.getCops().contains(stuck));
		verify(stuck, never()).destroy(any());
	}

	@Test
	@DisplayName("in view beyond 24 blocks is protected as far out as bystanders are, until twice Recycle_Seconds")
	void strandedInView_beyond24_keptUntilTwiceThreshold() {
		CopNpc young = stranded(CopState.PURSUING, 10, 66, 40, 13_000); // 30 blocks in front of him
		CopNpc old   = stranded(CopState.PURSUING, 11, 66, 40, 25_000);

		manager.spawnTick(playerId, wanted);

		assertTrue(group.getCops().contains(young));
		assertFalse(group.getCops().contains(old), "past 2x Recycle_Seconds only the 24-block view protects");
	}

	@Test
	@DisplayName("in view within 24 blocks is kept at any age")
	void strandedInView_within24_keptAtAnyAge() {
		CopNpc stuck = stranded(CopState.PURSUING, 10, 66, 30, 600_000); // 20 blocks in front of him

		manager.spawnTick(playerId, wanted);

		assertTrue(group.getCops().contains(stuck));
	}

	@Test
	@DisplayName("in the cone but behind a wall, or beyond Visibility_Check_Distance, counts as out of view")
	void inConeButBlockedOrFar_recycled() {
		CopNpc blocked = stranded(CopState.PURSUING, 10, 66, 30, 13_000);
		CopNpc far     = stranded(CopState.PURSUING, 11, 66, 70, 13_000);
		when(player.hasLineOfSight(blocked.getEntity())).thenReturn(false);

		manager.spawnTick(playerId, wanted);

		assertFalse(group.getCops().contains(blocked));
		assertFalse(group.getCops().contains(far));
	}

	@Test
	@DisplayName("a stranded cop within melee reach of the suspect is kept, even behind him")
	void strandedWithinMeleeReach_kept() {
		CopNpc stuck = stranded(CopState.COMBAT, 10, 64, 8, 600_000); // 2 blocks behind him, reach 3

		manager.spawnTick(playerId, wanted);

		assertTrue(group.getCops().contains(stuck));
		verify(stuck, never()).destroy(any());
	}

	@Test
	@DisplayName("a stranded cop within melee distance but a floor above the suspect is recycled")
	void strandedOverheadWithinReach_recycled() {
		CopNpc stuck = stranded(CopState.PURSUING, 10, 67, 10, 600_000); // 3 blocks straight up, slab between

		manager.spawnTick(playerId, wanted);

		assertFalse(group.getCops().contains(stuck));
	}

	@Test
	@DisplayName("the refill after a recycle never goes above Max_Per_Player")
	void recycleRefill_clampedToMaxPerPlayer() {
		when(fx.spawner.getTargetCopCount(anyInt())).thenReturn(5);
		when(fx.provider.getMaxCopsPerPlayer()).thenReturn(3);
		CopNpc stuck = stranded(CopState.PURSUING, 10, 64, 0, 13_000);
		stranded(CopState.PURSUING, 11, 64, 20, 0);
		stranded(CopState.PURSUING, 12, 64, 20, 0);

		manager.spawnTick(playerId, wanted);

		assertFalse(group.getCops().contains(stuck));
		assertEquals(3, group.getCops().size());
	}

	@Test
	@DisplayName("a stranded cop another player is looking at is kept")
	void strandedSeenByBystander_kept() {
		CopNpc stuck = stranded(CopState.PURSUING, 10, 64, 0, 13_000);
		when(fx.spawner.isVisibleToOtherPlayers(any(), eq(player))).thenReturn(true);

		manager.spawnTick(playerId, wanted);

		assertTrue(group.getCops().contains(stuck));
	}

	@Test
	@DisplayName("COMBAT is recycled like PURSUING; below the threshold, cuffing, or hunting someone else is not")
	void onlyHuntingCopsPastThreshold_recycled() {
		CopNpc combat   = stranded(CopState.COMBAT, 10, 64, 0, 13_000);
		CopNpc young    = stranded(CopState.PURSUING, 11, 64, 0, 11_000);
		CopNpc cuffing  = stranded(CopState.CUFFING, 12, 64, 0, 13_000);
		CopNpc attacker = stranded(CopState.COMBAT, 13, 64, 0, 13_000);
		attacker.setTargetPlayerId(UUID.randomUUID());

		manager.spawnTick(playerId, wanted);

		assertFalse(group.getCops().contains(combat));
		assertTrue(group.getCops().contains(young));
		assertTrue(group.getCops().contains(cuffing));
		assertTrue(group.getCops().contains(attacker));
	}

	@Test
	@DisplayName("Cops.Stuck.Enabled false recycles nothing")
	void disabled_recyclesNothing() {
		when(fx.provider.getStuckSettings()).thenReturn(new StuckSettings(false, 12, 60));
		CopNpc stuck = stranded(CopState.PURSUING, 10, 64, 0, 600_000);

		manager.spawnTick(playerId, wanted);

		assertTrue(group.getCops().contains(stuck));
	}

	@Test
	@DisplayName("the avoided spawner is allowed again once Avoid_Spawner_Seconds pass")
	void avoidedSpawner_expires() {
		stranded(CopState.PURSUING, 10, 64, 0, 13_000);
		manager.spawnTick(playerId, wanted);

		assertTrue(group.isAvoided(spawner, fx.clock[0] + 59_000));
		assertFalse(group.isAvoided(spawner, fx.clock[0] + 61_000));
		verify(fx.listeners.get(group), never()).onSignal(any(), eq(NpcSquadSignal.MAN_DOWN), any(), any());
	}
}
