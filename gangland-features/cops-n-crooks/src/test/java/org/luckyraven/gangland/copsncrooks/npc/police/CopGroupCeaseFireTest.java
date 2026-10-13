package org.luckyraven.gangland.copsncrooks.npc.police;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.21.0 fnd-squad: the per-target cease-fire flag (cops hold fire and cuff) and the standoff window (stuck cops are
 * not recycled). Neither is the {@code Stand_Down} radio line nor {@code CopManager.stoodDown}.
 */
@DisplayName("CopGroup - ceaseFire and standoffUntil flags")
class CopGroupCeaseFireTest {

	private final UUID     target = UUID.randomUUID();
	private final CopGroup group  = new CopGroup(target);

	@Test
	@DisplayName("a new group is not cease-fire and not in a standoff")
	void defaults_off() {
		assertFalse(group.isCeaseFire());
		assertFalse(group.isStandoff(0L));
	}

	@Test
	@DisplayName("setCeaseFire(true) then isCeaseFire is true; setCeaseFire(false) clears it")
	void setAndQuery() {
		group.setCeaseFire(true);
		assertTrue(group.isCeaseFire());

		group.setCeaseFire(false);
		assertFalse(group.isCeaseFire());
	}

	@Test
	@DisplayName("escalate(target) clears the flag and the Resisting line is still pending once")
	void escalateClearsCeaseFire() {
		group.setCeaseFire(true);

		group.escalate(target);

		assertFalse(group.isCeaseFire());
		assertTrue(group.pollResisting());
		assertFalse(group.pollResisting());
	}

	@Test
	@DisplayName("escalate(other id) keeps the flag and queues nothing")
	void escalateOtherTarget_keepsFlag() {
		group.setCeaseFire(true);

		group.escalate(UUID.randomUUID());

		assertTrue(group.isCeaseFire());
		assertFalse(group.pollResisting());
	}

	@Test
	@DisplayName("escalate without the flag ever set behaves as before: Resisting once, no flag")
	void escalateWithoutFlag_unchanged() {
		group.escalate(target);

		assertFalse(group.isCeaseFire());
		assertTrue(group.pollResisting());
	}

	@Test
	@DisplayName("clearCombatAlert clears both the cease-fire flag and the standoff window")
	void clearCombatAlertClearsBothFlags() {
		group.setCeaseFire(true);
		group.setStandoffUntil(1_000L);

		group.clearCombatAlert();

		assertFalse(group.isCeaseFire());
		assertFalse(group.isStandoff(0L));
	}

	@Test
	@DisplayName("the standoff window is half open: standoffUntil 1000 -> 999 inside, 1000 outside")
	void standoffWindow() {
		group.setStandoffUntil(1_000L);

		assertTrue(group.isStandoff(999L));
		assertFalse(group.isStandoff(1_000L));
		assertFalse(group.isStandoff(5_000L));
	}

	@Test
	@DisplayName("escalate does not touch the standoff window")
	void escalateKeepsStandoff() {
		group.setStandoffUntil(1_000L);

		group.escalate(target);

		assertTrue(group.isStandoff(999L));
	}
}
