package org.luckyraven.gangland.fights.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.events.gang.GangSafehouseSetEvent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FightArenaSafehouseListener - no safehouse inside an arena (GP-31)")
class FightArenaSafehouseListenerTest {

	private FightRig                   rig;
	private FightArenaSafehouseListener listener;

	@BeforeEach
	void setUp() {
		rig      = new FightRig();
		listener = new FightArenaSafehouseListener(rig.arenas);
	}

	@Test
	@DisplayName("a safehouse inside an arena box is refused, with or without a fight")
	void insideArena_cancelled() {
		GangSafehouseSetEvent event = new GangSafehouseSetEvent(1, rig.at(10, 1, 10), "base");

		listener.onSafehouse(event);

		assertTrue(event.isCancelled());
	}

	@Test
	@DisplayName("a safehouse outside every arena is untouched")
	void outside_allowed() {
		GangSafehouseSetEvent event = new GangSafehouseSetEvent(1, rig.at(100, 1, 100), "base");

		listener.onSafehouse(event);

		assertFalse(event.isCancelled());
	}
}
