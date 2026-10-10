package org.luckyraven.gangland.turf.npc.view;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.EffectType;
import org.luckyraven.gangland.turf.powerups.PowerupDefinition;
import org.luckyraven.gangland.turf.powerups.PowerupRegistry;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.inventory.flow.MenuFlow;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the boost catalogue paging (TF-18: nine per page, arrows only where a page exists) and the purchase
 * re-check it shares with the garrison panel (TF-19).
 */
@DisplayName("TurfPowerupBuffCatalogueView - paging and purchase re-check")
class TurfPowerupBuffCatalogueViewTest {

	private static List<PowerupDefinition> twelve() {
		List<PowerupDefinition> defs = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			defs.add(new PowerupDefinition("b%02d".formatted(i), "&aBoost", BigDecimal.TEN,
			                               EffectType.INCOME_MULTIPLIER, 1.25, 60));
		}
		return defs;
	}

	private static List<String> ids(List<PowerupDefinition> defs) {
		return defs.stream().map(PowerupDefinition::id).toList();
	}

	@Test
	@DisplayName("12 boosts make two pages: nine on the first, three on the second (TF-18)")
	void pages_nineAPage() {
		List<PowerupDefinition> all = twelve();

		assertEquals(2, TurfPowerupBuffCatalogueView.pages(all.size()));
		assertEquals(9, TurfPowerupBuffCatalogueView.page(all, 0).size());
		assertEquals(List.of("b09", "b10", "b11"), ids(TurfPowerupBuffCatalogueView.page(all, 1)));
	}

	@Test
	@DisplayName("the next arrow shows only while a later page exists, the previous arrow only after page one")
	void arrows_onlyWhereAPageExists() {
		assertTrue(TurfPowerupBuffCatalogueView.hasNext(0, 2));
		assertFalse(TurfPowerupBuffCatalogueView.hasNext(1, 2));
		assertFalse(TurfPowerupBuffCatalogueView.hasPrevious(0));
		assertTrue(TurfPowerupBuffCatalogueView.hasPrevious(1));
	}

	@Test
	@DisplayName("an empty catalogue still has one (empty) page")
	void pages_emptyIsOnePage() {
		assertEquals(1, TurfPowerupBuffCatalogueView.pages(0));
	}

	@Test
	@DisplayName("a boost purchase after the turf changed hands is refused, the bank is untouched, the panel closes")
	void purchase_turfChangedHands_refused() {
		PowerupRegistry registry = new PowerupRegistry();
		ActiveBuffManager buffs  = mock(ActiveBuffManager.class);
		QuartermasterAccess access = mock(QuartermasterAccess.class);
		EconomyHandler bank = mock(EconomyHandler.class);
		Gang gang = mock(Gang.class);
		when(gang.getEconomy()).thenReturn(bank);
		Turf turf = mock(Turf.class);
		TurfPowerupFlowSession session = new TurfPowerupFlowSession(turf, gang, gang, "Quartermaster");
		Player viewer = mock(Player.class);
		@SuppressWarnings("unchecked")
		MenuFlow<TurfPowerupFlowSession> flow = mock(MenuFlow.class);
		when(access.denyKey(viewer, session)).thenReturn("Quartermaster.Owner_Changed");

		TurfPowerupBuffCatalogueView view = new TurfPowerupBuffCatalogueView(registry, buffs, access,
		                                                                     mock(TurfMessages.class), "STONE", "&7");
		view.attemptBuy(flow, viewer, session, twelve().get(0));

		verify(flow).end();
		verify(bank, never()).withdrawAmount(any());
		verify(buffs, never()).activate(org.mockito.ArgumentMatchers.anyInt(), any());
	}
}
