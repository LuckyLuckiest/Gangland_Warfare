package org.luckyraven.gangland.copsncrooks.npc.police.perception;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.keystone.npc.NpcSquad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.doubleThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CopPerception}: the single seam where sight factors and lock-on filters meet the cop behaviours. With nothing
 * registered every answer is today's {@code cop.canSee(target, base)} / {@code base}.
 */
@DisplayName("CopPerception - sight factors, lock-on filters and the pursuit range")
class CopPerceptionTest {

	private static final double BASE = 40.0;

	private CopPerception perception;
	private CopNpc        cop;
	private Player        player;

	@BeforeEach
	void setUp() {
		perception = new CopPerception();
		cop        = mock(CopNpc.class);
		player     = mock(Player.class);
	}

	private static boolean near(double expected, double actual) {
		return Math.abs(expected - actual) < 1e-9;
	}

	@Test
	@DisplayName("no factors: rangeFor is the base range and sees delegates canSee(target, base)")
	void noFactorsEqualsCanSee() {
		when(cop.canSee(player, BASE)).thenReturn(true);

		assertEquals(BASE, perception.rangeFor(cop, player, BASE));
		assertTrue(perception.sees(cop, player, BASE));
		verify(cop).canSee(player, BASE);
	}

	@Test
	@DisplayName("factors multiply: 0.5 and 0.8 hand canSee a range of base x 0.4")
	void factorsMultiply() {
		perception.addSightFactor((c, t) -> 0.5);
		perception.addSightFactor((c, t) -> 0.8);

		perception.sees(cop, player, BASE);

		assertEquals(BASE * 0.4, perception.rangeFor(cop, player, BASE), 1e-9);
		verify(cop).canSee(eq(player), doubleThat(range -> near(16.0, range)));
	}

	@Test
	@DisplayName("a factor is asked about the cop and the player it scales")
	void factorReceivesCopAndPlayer() {
		CopPerception.SightFactor factor = mock(CopPerception.SightFactor.class);
		when(factor.factor(cop, player)).thenReturn(0.25);
		perception.addSightFactor(factor);

		assertEquals(10.0, perception.rangeFor(cop, player, BASE), 1e-9);
	}

	@Test
	@DisplayName("a lock-on filter that says no blocks acquisition only: sees is unchanged")
	void lockOnFilterBlocksAcquireOnly() {
		when(cop.canSee(player, BASE)).thenReturn(true);
		perception.addLockOnFilter((c, p) -> false);

		assertFalse(perception.allowsLockOn(cop, player));
		assertFalse(perception.acquires(cop, player, BASE));
		assertTrue(perception.sees(cop, player, BASE));
	}

	@Test
	@DisplayName("acquires needs every filter to allow and the cop to see")
	void acquiresNeedsAllFiltersAndSight() {
		when(cop.canSee(player, BASE)).thenReturn(true);
		perception.addLockOnFilter((c, p) -> true);
		assertTrue(perception.allowsLockOn(cop, player));
		assertTrue(perception.acquires(cop, player, BASE));

		perception.addLockOnFilter((c, p) -> false);
		assertFalse(perception.acquires(cop, player, BASE), "one refusing filter is enough");

		CopPerception open = new CopPerception();
		when(cop.canSee(player, BASE)).thenReturn(false);
		assertTrue(open.allowsLockOn(cop, player));
		assertFalse(open.acquires(cop, player, BASE), "allowed but unseen is not acquired");
	}

	@Test
	@DisplayName("a non-player target ignores every factor and filter")
	void nonPlayerTargetIgnoresFactors() {
		LivingEntity zombie = mock(LivingEntity.class);
		CopPerception.SightFactor factor = mock(CopPerception.SightFactor.class);
		perception.addSightFactor(factor);
		perception.addLockOnFilter((c, p) -> false);
		when(cop.canSee(zombie, BASE)).thenReturn(true);

		assertEquals(BASE, perception.rangeFor(cop, zombie, BASE));
		assertEquals(BASE, perception.pursuitRange(cop, zombie, null, BASE));
		assertTrue(perception.allowsLockOn(cop, zombie));
		assertTrue(perception.acquires(cop, zombie, BASE));
		verify(factor, never()).factor(any(), any());
	}

	@Test
	@DisplayName("pursuitRange with nothing registered is the base; a 0.4 factor scales it")
	void pursuitRangeNoFiltersIsRangeFor() {
		assertEquals(BASE, perception.pursuitRange(cop, player, null, BASE));

		perception.addSightFactor((c, p) -> 0.4);
		assertEquals(BASE * 0.4, perception.pursuitRange(cop, player, null, BASE), 1e-9);
	}

	@Test
	@DisplayName("pursuitRange is 0 for a filtered-out player when the squad is stale or absent")
	void pursuitRangeZeroWhenRejectedAndStale() {
		perception.addLockOnFilter((c, p) -> false);

		assertEquals(0.0, perception.pursuitRange(cop, player, new NpcSquad(), BASE));
		assertEquals(0.0, perception.pursuitRange(cop, player, null, BASE));
	}

	@Test
	@DisplayName("pursuitRange keeps the scaled range for a filtered-out player while the squad sighting is fresh")
	void pursuitRangeKeptWhileSquadFresh() {
		perception.addLockOnFilter((c, p) -> false);
		perception.addSightFactor((c, p) -> 0.5);
		NpcSquad squad = new NpcSquad();
		squad.reportSighting(new Location(mock(World.class), 0, 64, 0));
		assertTrue(squad.hasFreshSighting());

		assertEquals(20.0, perception.pursuitRange(cop, player, squad, BASE), 1e-9);
	}

	@Test
	@DisplayName("an allowed player is pursued at rangeFor whatever the squad says")
	void pursuitRangeAllowedIgnoresSquad() {
		perception.addSightFactor((c, p) -> 0.5);

		assertEquals(20.0, perception.pursuitRange(cop, player, new NpcSquad(), BASE), 1e-9);
	}

	@Test
	@DisplayName("a factor registering another factor mid-evaluation does not throw (copy-on-write lists)")
	void registeringDuringEvaluationDoesNotThrow() {
		perception.addSightFactor((c, p) -> {
			perception.addSightFactor((c2, p2) -> 1.0);
			return 1.0;
		});

		assertEquals(BASE, perception.rangeFor(cop, player, BASE), 1e-9);
	}
}
