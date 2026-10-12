package org.luckyraven.gangland.turf.listener.powerups;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.events.CivilianDeathEvent;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("TurfDefenderDeathListener - reports civilian deaths to the defender deployer (T-251)")
class TurfDefenderDeathListenerTest {

	@Test
	@DisplayName("a CivilianDeathEvent for a guard marks that guard as killed")
	void civilianDeath_marksTheGuardKilled() {
		TurfDefenderDeployer deployer = mock(TurfDefenderDeployer.class);
		CivilianNpc npc = mock(CivilianNpc.class);

		new TurfDefenderDeathListener(deployer).onCivilianDeath(new CivilianDeathEvent(npc, null, 0.0));

		verify(deployer).markKilled(npc);
	}
}
