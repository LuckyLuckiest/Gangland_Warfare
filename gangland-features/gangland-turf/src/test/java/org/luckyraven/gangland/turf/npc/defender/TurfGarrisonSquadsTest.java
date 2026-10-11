package org.luckyraven.gangland.turf.npc.defender;

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("TurfGarrisonSquads")
class TurfGarrisonSquadsTest {

	@Test
	@DisplayName("two turfs never share a squad scope, and squadFor delegates with the turf scope")
	void scopePerTurf() {
		CivilianService service = mock(CivilianService.class);
		TurfGarrisonSquads one = new TurfGarrisonSquads(service, 1, "VIP");
		TurfGarrisonSquads two = new TurfGarrisonSquads(service, 2, "VIP");
		assertEquals("turf:1", one.scope());
		assertEquals("turf:2", two.scope());
		assertNotEquals(one.scope(), two.scope());

		CivilianNpc npc = mock(CivilianNpc.class);
		LivingEntity target = mock(LivingEntity.class);
		two.squadFor(npc, target);
		verify(service).squadFor(npc, target, "turf:2");
	}
}
