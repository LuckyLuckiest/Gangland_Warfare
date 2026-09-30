package org.luckyraven.gangland.copsncrooks.npc.police.npc;

import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SneakTrait;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.npc.TacticsConfig;
import org.luckyraven.keystone.npc.NpcDifficulty;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CopNpc field care - limp on both navigator parameter sets, crouch while under care, a state change ends care")
class CopNpcFieldCareTest {

	private NPC                 npc;
	private NavigatorParameters defaults;
	private NavigatorParameters local;
	private SneakTrait          sneak;
	private CopNpc              cop;

	@BeforeEach
	void setUp() {
		npc      = mock(NPC.class, RETURNS_DEEP_STUBS);
		defaults = mock(NavigatorParameters.class);
		local    = mock(NavigatorParameters.class);
		sneak    = mock(SneakTrait.class);
		when(npc.getNavigator().getDefaultParameters()).thenReturn(defaults);
		when(npc.getNavigator().getLocalParameters()).thenReturn(local);
		doReturn(sneak).when(npc).getOrAddTrait(SneakTrait.class);
		cop = new CopNpc(mock(JavaPlugin.class), npc, tier(), Map.of(), new Location(null, 0, 0, 0),
		                 mock(CopConfigProvider.class));
	}

	@Test
	@DisplayName("applySpeed sets the tier Speed times the multiplier on the default AND the local parameters")
	void applySpeed_setsDefaultAndLocal() {
		cop.applySpeed(0.5);

		verify(defaults).speedModifier(0.6f);
		verify(local).speedModifier(0.6f);
	}

	@Test
	@DisplayName("under care crouches (SneakTrait) and standing up again un-crouches")
	void underCare_togglesSneak() {
		cop.setUnderCare(true);
		assertTrue(cop.isUnderCare());
		verify(sneak).setSneaking(true);

		cop.setUnderCare(false);
		assertFalse(cop.isUnderCare());
		verify(sneak).setSneaking(false);
	}

	@Test
	@DisplayName("a state change outside PURSUING/COMBAT ends care on both sides and any walk to cover; PURSUING -> COMBAT keeps them")
	void transition_endsCareOutsideFightingStates() {
		CopNpc patient = mock(CopNpc.class);
		cop.setPatient(patient);
		cop.setUnderCare(true);

		cop.transitionTo(CopState.PURSUING);
		cop.setMovingToCover(true);
		cop.transitionTo(CopState.COMBAT);
		assertSame(patient, cop.getPatient());
		assertTrue(cop.isUnderCare());
		assertTrue(cop.isMovingToCover(), "field care must still see the walk to cover on the hop");

		cop.transitionTo(CopState.RETURNING);
		assertNull(cop.getPatient());
		assertFalse(cop.isUnderCare());
		assertFalse(cop.isMovingToCover());
		verify(sneak).setSneaking(false);
	}

	private static CopTierConfig tier() {
		return new CopTierConfig(1, "&9Officer", 40.0, 2.0, 1.2, 2.0, false, false, List.of(), List.of(), null, null,
		                         null, null, NpcDifficulty.EASY, TacticsConfig.DEFAULT, 0.25);
	}
}
