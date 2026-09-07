package org.luckyraven.gangland.gadget.car.access;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link CarAccessPolicy}. The placer UUID was written to the entity PDC and the {@code parked_car} row from
 * the day cars shipped and never read back, so mount, refuel and pickup were open to every player — pickup handing
 * the thief the car item outright.
 *
 * <p>Observation #6 (gadgets-cars-fuel-jetpack.md), docket GD-06.
 */
@DisplayName("CarAccessPolicy - who may mount, refuel or pick up a placed car")
class CarAccessPolicyTest {

	private static final UUID OWNER    = UUID.randomUUID();
	private static final UUID OUTSIDER = UUID.randomUUID();

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
	@DisplayName("a stranger cannot touch someone else's car - the GD-06 defect")
	void outsiderIsDenied() {
		assertFalse(policy(false).canUse(player(OUTSIDER, false), OWNER));
	}

	@Test
	void placerAlwaysHasAccess() {
		assertTrue(policy(false).canUse(player(OWNER, false), OWNER));
	}

	@Test
	@DisplayName("gang mates share the car, so a gang vehicle keeps working")
	void gangMateHasAccess() {
		assertTrue(policy(true).canUse(player(OUTSIDER, false), OWNER));
	}

	@Test
	@DisplayName("staff bypass works without joining the gang")
	void bypassPermissionGrantsAccess() {
		assertTrue(policy(false).canUse(player(OUTSIDER, true), OWNER));
	}

	@Test
	@DisplayName("a car placed before GD-06 has no recorded placer and stays usable by everyone")
	void unownedCarStaysOpen() {
		assertTrue(policy(false).canUse(player(OUTSIDER, false), null));
	}

	@Test
	void nullPlayerIsDenied() {
		assertFalse(policy(true).canUse(null, OWNER));
	}

	@Test
	@DisplayName("a missing gang contract degrades to owner-only, never to open access")
	void nullContractDeniesOutsiders() {
		CarAccessPolicy policy = new CarAccessPolicy(null);

		assertFalse(policy.canUse(player(OUTSIDER, false), OWNER));
		assertTrue(policy.canUse(player(OWNER, false), OWNER));
	}

}
