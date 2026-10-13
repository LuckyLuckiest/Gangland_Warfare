package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.bartizan.api.event.WeaponShootEvent;
import org.luckyraven.bartizan.api.weapon.Weapon;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.SightModel;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("SightShotListener - a player's gunshot resets his sight factor")
class SightShotListenerTest {

	private final SightModel        model    = mock(SightModel.class);
	private final SightShotListener listener = new SightShotListener(model);

	private WeaponShootEvent shot(LivingEntity who) {
		return new WeaponShootEvent(mock(Weapon.class), who);
	}

	@Test
	@DisplayName("playerShooterRecorded: WeaponShootEvent by a player -> shot(player)")
	void playerShooterRecorded() {
		Player shooter = mock(Player.class);

		listener.onWeaponShoot(shot(shooter));

		verify(model).shot(shooter);
	}

	@Test
	@DisplayName("a non-player shooter (an NPC) is not recorded")
	void npcShooterIgnored() {
		listener.onWeaponShoot(shot(mock(LivingEntity.class)));

		verifyNoInteractions(model);
	}

	@Test
	@DisplayName("scanned listener at MONITOR ignoring cancelled shots (a restrained shooter's shot is cancelled)")
	void registeredAtMonitorIgnoringCancelled() throws NoSuchMethodException {
		assertNotNull(SightShotListener.class.getAnnotation(ListenerHandler.class));

		EventHandler handler = SightShotListener.class.getMethod("onWeaponShoot", WeaponShootEvent.class)
		                                              .getAnnotation(EventHandler.class);
		assertNotNull(handler);
		assertEquals(EventPriority.MONITOR, handler.priority());
		assertTrue(handler.ignoreCancelled());
	}
}
