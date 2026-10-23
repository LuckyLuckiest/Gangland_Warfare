package org.luckyraven.gangland.gadget.car.access;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 0.20.0 hotwire additions to {@link CarAccessPolicy}: {@code canHotwire}, the session grant and {@code canMount}.
 * {@code canUse} itself stays exactly as GD-06 pinned it (CarAccessPolicyTest is untouched).
 */
@DisplayName("CarAccessPolicy - hotwire grant")
class CarAccessPolicyHotwireTest {

	private static final UUID OWNER    = UUID.randomUUID();
	private static final UUID THIEF    = UUID.randomUUID();
	private static final UUID BYSTAND  = UUID.randomUUID();
	private static final UUID CAR      = UUID.randomUUID();
	private static final UUID OTHERCAR = UUID.randomUUID();

	private static Player player(UUID uuid, boolean bypass) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.hasPermission(CarAccessPolicy.BYPASS_PERMISSION)).thenReturn(bypass);
		return player;
	}

	private static CarAccessPolicy policy(boolean sharesGang) {
		return new CarAccessPolicy((one, other) -> sharesGang);
	}

	@Test
	@DisplayName("canHotwireOnlyWhenCannotUse: placer self -> false, stranger -> true, null placer -> false")
	void canHotwireOnlyWhenCannotUse() {
		CarAccessPolicy policy = policy(false);

		assertFalse(policy.canHotwire(player(OWNER, false), OWNER));
		assertTrue(policy.canHotwire(player(THIEF, false), OWNER));
		assertFalse(policy.canHotwire(player(THIEF, false), null));
	}

	@Test
	@DisplayName("canHotwireFalseForGangMateStaffAndNullPlayer: they may simply use the car, or there is nobody")
	void canHotwireFalseForGangMateStaffAndNullPlayer() {
		assertFalse(policy(true).canHotwire(player(THIEF, false), OWNER));
		assertFalse(policy(false).canHotwire(player(THIEF, true), OWNER));
		assertFalse(policy(false).canHotwire(null, OWNER));
	}

	@Test
	@DisplayName("grantLetsThiefMountNotPickUp: after grant canMount true, canUse still false")
	void grantLetsThiefMountNotPickUp() {
		CarAccessPolicy policy = policy(false);
		Player          thief  = player(THIEF, false);

		assertFalse(policy.canMount(thief, OWNER, CAR));

		policy.grant(CAR, THIEF);

		assertTrue(policy.canMount(thief, OWNER, CAR));
		assertFalse(policy.canUse(thief, OWNER), "pickup and refuel keep canUse - ownership never transfers (T-73)");
		assertEquals(THIEF, policy.grantedTo(CAR));
	}

	@Test
	@DisplayName("grantIsPerCarAndPerThief: another car, another player, or null stay locked")
	void grantIsPerCarAndPerThief() {
		CarAccessPolicy policy = policy(false);
		policy.grant(CAR, THIEF);

		assertFalse(policy.canMount(player(THIEF, false), OWNER, OTHERCAR));
		assertFalse(policy.canMount(player(BYSTAND, false), OWNER, CAR));
		assertFalse(policy.canMount(null, OWNER, CAR));
		assertNull(policy.grantedTo(OTHERCAR));
	}

	@Test
	@DisplayName("revokeDropsTheGrant: a picked-up car is locked to the thief again")
	void revokeDropsTheGrant() {
		CarAccessPolicy policy = policy(false);
		policy.grant(CAR, THIEF);

		policy.revoke(CAR);

		assertFalse(policy.canMount(player(THIEF, false), OWNER, CAR));
		assertNull(policy.grantedTo(CAR));
	}

	@Test
	@DisplayName("revokeWithoutGrantIsHarmless")
	void revokeWithoutGrantIsHarmless() {
		policy(false).revoke(CAR);
	}

	@Test
	@DisplayName("canMountKeepsEveryCanUseRule: owner, gang mate, staff, unowned car all still mount")
	void canMountKeepsEveryCanUseRule() {
		assertTrue(policy(false).canMount(player(OWNER, false), OWNER, CAR));
		assertTrue(policy(true).canMount(player(THIEF, false), OWNER, CAR));
		assertTrue(policy(false).canMount(player(THIEF, true), OWNER, CAR));
		assertTrue(policy(false).canMount(player(THIEF, false), null, CAR));
		assertFalse(policy(false).canMount(player(THIEF, false), OWNER, CAR));
		assertFalse(policy(false).canMount(null, OWNER, CAR));
	}

	@Test
	@DisplayName("secondGrantReplacesFirst: only one thief holds a car at a time")
	void secondGrantReplacesFirst() {
		CarAccessPolicy policy = policy(false);
		policy.grant(CAR, THIEF);
		policy.grant(CAR, BYSTAND);

		assertEquals(BYSTAND, policy.grantedTo(CAR));
		assertFalse(policy.canMount(player(THIEF, false), OWNER, CAR));
	}
}
