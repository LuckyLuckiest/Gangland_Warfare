package org.luckyraven.gangland.civilians.npc.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.state.behavior.CivilianCallingBehavior;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;

@DisplayName("CivilianBehaviorFactory - CALLING has a behavior of its own per NPC")
class CivilianBehaviorFactoryCallingTest {

	@Test
	@DisplayName("every CivilianState, CALLING included, gets a behavior and CALLING is a CivilianCallingBehavior")
	void callingBehaviorCreated() {
		Map<CivilianState, CivilianBehavior> behaviors = new CivilianBehaviorFactory(16.0).createBehaviors();

		assertEquals(CivilianState.values().length, behaviors.size());
		assertInstanceOf(CivilianCallingBehavior.class, behaviors.get(CivilianState.CALLING));
	}

	@Test
	@DisplayName("two NPCs never share a calling behavior instance")
	void callingBehaviorNotShared() {
		CivilianBehaviorFactory factory = new CivilianBehaviorFactory(16.0);

		assertNotSame(factory.createBehaviors().get(CivilianState.CALLING),
		              factory.createBehaviors().get(CivilianState.CALLING));
	}
}
