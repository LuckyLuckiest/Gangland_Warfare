package org.luckyraven.gangland.civilians.listener.civilian;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.witness.WitnessCall;
import org.luckyraven.gangland.civilians.witness.WitnessService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("WitnessListener - caller death and criminal logout end or hand over the call")
class WitnessListenerTest {

	private final CivilianService civilians = mock(CivilianService.class);
	private final WitnessService  witnesses = mock(WitnessService.class);
	private final WitnessListener listener  = new WitnessListener(civilians, witnesses);

	@Test
	@DisplayName("the death of a civilian with a running call hands the call over")
	void callerDeathHandsOver() {
		CivilianNpc npc = civilianWithCall(true);
		listener.onCivilianDeath(deathOf(npc));

		verify(witnesses).onCallerDeath(npc);
	}

	@Test
	@DisplayName("the death of a civilian with no call does nothing")
	void plainCivilianDeathIgnored() {
		CivilianNpc npc = civilianWithCall(false);
		listener.onCivilianDeath(deathOf(npc));

		verify(witnesses, never()).onCallerDeath(any());
	}

	@Test
	@DisplayName("the death of something that is not a registered civilian does nothing")
	void unknownEntityDeathIgnored() {
		LivingEntity cow = mock(LivingEntity.class);
		when(cow.getUniqueId()).thenReturn(UUID.randomUUID());
		EntityDeathEvent event = mock(EntityDeathEvent.class);
		when(event.getEntity()).thenReturn(cow);

		listener.onCivilianDeath(event);

		verify(witnesses, never()).onCallerDeath(any());
	}

	@Test
	@DisplayName("a player quitting cancels the calls about them")
	void quitCancels() {
		Player player = mock(Player.class);
		UUID   id     = UUID.randomUUID();
		when(player.getUniqueId()).thenReturn(id);
		PlayerQuitEvent event = mock(PlayerQuitEvent.class);
		when(event.getPlayer()).thenReturn(player);

		listener.onQuit(event);

		verify(witnesses).onCriminalQuit(id);
	}

	private CivilianNpc civilianWithCall(boolean calling) {
		CivilianNpc  npc    = mock(CivilianNpc.class);
		LivingEntity entity = mock(LivingEntity.class);
		UUID         id     = UUID.randomUUID();
		when(entity.getUniqueId()).thenReturn(id);
		when(npc.getEntity()).thenReturn(entity);
		WitnessCall call = calling ? mock(WitnessCall.class) : null;
		when(npc.getWitnessCall()).thenReturn(call);
		when(civilians.getNpc(id)).thenReturn(npc);
		return npc;
	}

	private EntityDeathEvent deathOf(CivilianNpc npc) {
		LivingEntity entity = npc.getEntity();
		EntityDeathEvent event = mock(EntityDeathEvent.class);
		when(event.getEntity()).thenReturn(entity);
		return event;
	}
}
