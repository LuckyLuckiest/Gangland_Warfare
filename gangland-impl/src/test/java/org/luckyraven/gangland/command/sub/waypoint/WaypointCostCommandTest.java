package org.luckyraven.gangland.command.sub.waypoint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A negative cost used to be stored as typed; TeleportCommand then passed the balance check, and
 * {@code withdrawAmount} threw inside its async chain, so the teleport happened for free.
 */
@DisplayName("WaypointCostCommand.validCost - only finite, non-negative costs are stored")
class WaypointCostCommandTest {

	@Test
	void negativeCost_isRejected() {
		assertFalse(WaypointCostCommand.validCost(-500));
	}

	@Test
	void overflowingCost_isRejected() {
		assertFalse(WaypointCostCommand.validCost(Double.parseDouble("1E400")), "parses to Infinity");
	}

	@Test
	void zeroAndPositiveCosts_areAccepted() {
		assertTrue(WaypointCostCommand.validCost(0), "a free waypoint is a legitimate configuration");
		assertTrue(WaypointCostCommand.validCost(25.5));
	}
}
