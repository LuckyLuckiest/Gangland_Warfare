package org.luckyraven.gangland.civilians.listener.civilian;

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.bartizan.api.event.WeaponShootEvent;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.witness.WitnessTestKit;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("WitnessShotListener - a player's gunshot makes his next crime loud (0.21.1)")
class WitnessShotListenerTest {

	private final WitnessTestKit      kit      = new WitnessTestKit();
	private final WitnessShotListener listener = new WitnessShotListener(kit.service);

	@Test
	@DisplayName("a player's shot is heard: a civilian with its back turned turns to the noise and calls")
	void playerShotIsHeard() {
		CivilianNpc away  = kit.civilian(10, true, 270f);
		WeaponShootEvent event = mock(WeaponShootEvent.class);
		when(event.getShooter()).thenReturn(kit.criminal);

		listener.onWeaponShoot(event);

		assertEquals(1, kit.witness("Car_Theft"));
		assertEquals(CivilianState.CALLING, away.getCurrentState());
	}

	@Test
	@DisplayName("an NPC's shot is not the player's: his crime stays silent")
	void npcShotIgnored() {
		CivilianNpc away  = kit.civilian(10, true, 270f);
		WeaponShootEvent event = mock(WeaponShootEvent.class);
		when(event.getShooter()).thenReturn(mock(LivingEntity.class));

		listener.onWeaponShoot(event);

		assertEquals(0, kit.witness("Car_Theft"));
		assertEquals(CivilianState.IDLE, away.getCurrentState());
	}

	@Test
	@DisplayName("registered only when Bartizan is present")
	void bartizanGated() {
		assertEquals("isBartizanAvailable", WitnessShotListener.class.getAnnotation(ListenerHandler.class).condition());
	}
}
