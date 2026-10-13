package org.luckyraven.gangland.turf.npc.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.bukkit.entity.Player;
import org.luckyraven.keystone.economy.EconomyHandler;

import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** W7-B: the hub info item gains "guards on duty" and "who may spend". */
@DisplayName("TurfPowerupMenuView - info lore (W7-B)")
class TurfPowerupMenuViewTest {

	private static final int TURF_ID = 4;

	private IntUnaryOperator    aliveGuards;
	private GarrisonManager     garrisons;
	private QuartermasterAccess access;
	private TurfMessages        messages;
	private TurfPowerupMenuView view;
	private Turf                turf;
	private Gang                owner;
	private Gang                ally;

	@BeforeEach
	void setUp() {
		garrisons = mock(GarrisonManager.class);
		access    = mock(QuartermasterAccess.class);
		ActiveBuffManager buffs     = mock(ActiveBuffManager.class);
		when(buffs.active(TURF_ID)).thenReturn(List.of());
		aliveGuards = mock(IntUnaryOperator.class);
		messages = mock(TurfMessages.class);
		when(messages.text(anyString(), anyString(), any(Object[].class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(messages.text(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
		view = new TurfPowerupMenuView(garrisons, buffs, aliveGuards, access, messages, "STONE", "&7");

		turf = mock(Turf.class);
		when(turf.getId()).thenReturn(TURF_ID);
		when(turf.getDisplayName()).thenReturn("Alpha");
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

	@Test
	@DisplayName("infoLore_showsOrder: the hub tells every viewer the turf's guard order")
	void infoLore_showsOrder() {
		when(garrisons.holding(TURF_ID)).thenReturn(false);

		List<String> lore = view.infoLore(turf, owner, owner);

		assertTrue(lore.contains("Quartermaster.Menu_Order"));
		verify(messages).text("Quartermaster.Order_Defend", "&cDefend");
	}

	private TurfPowerupFlowSession session() {
		TurfPowerupFlowSession session = mock(TurfPowerupFlowSession.class);
		when(session.getTurf()).thenReturn(turf);
		return session;
	}

	@Test
	@DisplayName("toggleOrder_ownerFlipsAndTells: an owner click switches Defend to Hold, tells the viewer and re-renders")
	void toggleOrder_ownerFlipsAndTells() {
		Player viewer = mock(Player.class);
		MenuFlow<TurfPowerupFlowSession> flow = mock(MenuFlow.class);
		TurfPowerupFlowSession session = session();
		when(access.denyKey(viewer, session)).thenReturn(null);
		when(garrisons.holding(TURF_ID)).thenReturn(false);

		view.toggleOrder(flow, viewer, session);

		verify(garrisons).setHolding(TURF_ID, true);
		verify(viewer).sendMessage("Quartermaster.Order_Set");
		verify(flow).rerender();
	}

	@Test
	@DisplayName("toggleOrder_allyOrNoRank_refusedAndUnchanged: a denied click changes nothing; a closing refusal ends the flow")
	void toggleOrder_allyOrNoRank_refusedAndUnchanged() {
		Player viewer = mock(Player.class);
		MenuFlow<TurfPowerupFlowSession> flow = mock(MenuFlow.class);
		TurfPowerupFlowSession session = session();

		when(access.denyKey(viewer, session)).thenReturn(QuartermasterAccess.ALLIES_LOOK);
		view.toggleOrder(flow, viewer, session);
		when(access.denyKey(viewer, session)).thenReturn(QuartermasterAccess.NO_RANK);
		view.toggleOrder(flow, viewer, session);

		verify(garrisons, never()).setHolding(anyInt(), anyBoolean());
		verify(viewer, org.mockito.Mockito.times(2)).sendMessage(org.mockito.ArgumentMatchers.anyString());
		verify(flow, org.mockito.Mockito.times(2)).rerender();

		when(access.denyKey(viewer, session)).thenReturn(QuartermasterAccess.OWNER_CHANGED);
		view.toggleOrder(flow, viewer, session);
		verify(flow).end();
		verify(garrisons, never()).setHolding(anyInt(), anyBoolean());
	}
}
