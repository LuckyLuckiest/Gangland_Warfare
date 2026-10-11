package org.luckyraven.gangland.turf.npc.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.keystone.economy.EconomyHandler;

import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** W7-B: the hub info item gains "guards on duty" and "who may spend". */
@DisplayName("TurfPowerupMenuView - info lore (W7-B)")
class TurfPowerupMenuViewTest {

	private static final int TURF_ID = 4;

	private IntUnaryOperator    aliveGuards;
	private TurfPowerupMenuView view;
	private Turf                turf;
	private Gang                owner;
	private Gang                ally;

	@BeforeEach
	void setUp() {
		GarrisonManager   garrisons = mock(GarrisonManager.class);
		ActiveBuffManager buffs     = mock(ActiveBuffManager.class);
		when(buffs.active(TURF_ID)).thenReturn(List.of());
		aliveGuards = mock(IntUnaryOperator.class);
		TurfMessages messages = mock(TurfMessages.class);
		when(messages.text(anyString(), anyString(), any(Object[].class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(messages.text(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
		view = new TurfPowerupMenuView(garrisons, buffs, aliveGuards, messages, "STONE", "&7");

		turf = mock(Turf.class);
		when(turf.getId()).thenReturn(TURF_ID);
		owner = mock(Gang.class);
		when(owner.getName()).thenReturn("Owners");
		when(owner.getEconomy()).thenReturn(new EconomyHandler(null));
		ally = mock(Gang.class);
		when(ally.getName()).thenReturn("Friends");
		when(ally.getEconomy()).thenReturn(new EconomyHandler(null));
	}

	@Test
	@DisplayName("infoLore_owner: on-duty and spend-owner lines, no ally lines")
	void infoLore_owner() {
		List<String> lore = view.infoLore(turf, owner, owner);

		assertTrue(lore.contains("Quartermaster.Menu_On_Duty"));
		assertTrue(lore.contains("Quartermaster.Menu_Spend_Owner"));
		assertFalse(lore.contains("Quartermaster.Menu_Allied"));
		assertFalse(lore.contains("Quartermaster.Menu_Spend_Ally"));
		verify(aliveGuards).applyAsInt(TURF_ID);
	}

	@Test
	@DisplayName("infoLore_ally: on-duty, allied and spend-ally lines")
	void infoLore_ally() {
		List<String> lore = view.infoLore(turf, owner, ally);

		assertTrue(lore.contains("Quartermaster.Menu_On_Duty"));
		assertTrue(lore.contains("Quartermaster.Menu_Allied"));
		assertTrue(lore.contains("Quartermaster.Menu_Spend_Ally"));
		assertFalse(lore.contains("Quartermaster.Menu_Spend_Owner"));
		verify(aliveGuards).applyAsInt(TURF_ID);
	}
}
