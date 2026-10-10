package org.luckyraven.gangland.turf.npc.view;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfRules;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.EffectType;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.gangland.turf.powerups.PowerupRegistry;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.inventory.flow.MenuFlow;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the Quartermaster garrison purchase ({@link TurfPowerupGarrisonView#attemptBuy}): price from
 * {@code turf_powerups.yml} (TF-17), the garrison_discount buff (TF-02), the rank gate re-checked on every click
 * (TF-19, TF-45) and the reserve cap of {@code turf_rules.yml} Garrison.Max_Reserve. The shared pieces are mocks;
 * {@link QuartermasterAccess} has its own test.
 */
@DisplayName("TurfPowerupGarrisonView - guard purchases")
class TurfPowerupGarrisonViewTest {

	private static final int TURF_ID = 7;

	private GarrisonManager           garrisons;
	private PowerupRegistry           registry;
	private ActiveBuffManager         buffs;
	private QuartermasterAccess       access;
	private TurfRules                 rules;
	private EconomyHandler            bank;
	private MenuFlow<TurfPowerupFlowSession> flow;
	private Player                    viewer;
	private TurfPowerupFlowSession    session;
	private TurfPowerupGarrisonView   view;
	private TurfMessages              messages;

	@BeforeEach
	void setUp() {
		garrisons = mock(GarrisonManager.class);
		registry  = new PowerupRegistry();
		registry.setGarrisonCost(BigDecimal.valueOf(1000));
		buffs  = mock(ActiveBuffManager.class);
		access = mock(QuartermasterAccess.class);
		rules  = mock(TurfRules.class);
		when(buffs.effectiveMultiplier(TURF_ID, EffectType.GARRISON_DISCOUNT)).thenReturn(1.0);
		when(rules.getMaxReserve()).thenReturn(12);

		bank = new EconomyHandler(null);
		bank.setAmount(BigDecimal.valueOf(50_000));
		Gang gang = mock(Gang.class);
		when(gang.getEconomy()).thenReturn(bank);

		Turf turf = mock(Turf.class);
		when(turf.getId()).thenReturn(TURF_ID);
		session = new TurfPowerupFlowSession(turf, gang, gang, "Quartermaster");

		flow   = mock(MenuFlow.class);
		viewer = mock(Player.class);

		messages = mock(TurfMessages.class);
		when(messages.text(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
		view = new TurfPowerupGarrisonView(garrisons, registry, buffs, access, rules,
		                                   messages, "STONE", "&7");
	}

	@Test
	@DisplayName("the guard price comes from the registry, which turf_powerups.yml fills (TF-17)")
	void price_fromYaml() {
		registry.setGarrisonCost(new BigDecimal("1234"));

		view.attemptBuy(flow, viewer, session);

		assertPaid("1234");
		verify(garrisons).add(TURF_ID, 1);
	}

	@Test
	@DisplayName("an active garrison_discount buff makes the guard cheaper (TF-02)")
	void discount_applies() {
		when(buffs.effectiveMultiplier(TURF_ID, EffectType.GARRISON_DISCOUNT)).thenReturn(0.8);

		view.attemptBuy(flow, viewer, session);

		assertPaid("800");
		verify(garrisons).add(TURF_ID, 1);
	}

	@Test
	@DisplayName("a recruit without gangland.turf.upgrade cannot spend the bank (TF-45, plan exit 1)")
	void recruit_cannotSpendTheBank() {
		when(access.denyKey(viewer, session)).thenReturn("Quartermaster.No_Rank");

		view.attemptBuy(flow, viewer, session);

		assertUnpaid();
		verify(garrisons, never()).add(anyInt(), anyInt());
	}

	@Test
	@DisplayName("a turf already holding Max_Reserve guards cannot take another (plan exit 2)")
	void reserveCap_stopsAt12() {
		when(garrisons.count(TURF_ID)).thenReturn(12);

		view.attemptBuy(flow, viewer, session);

		assertUnpaid();
		verify(garrisons, never()).add(anyInt(), anyInt());
	}

	@Test
	@DisplayName("a turf that changed hands while the panel was open is refused and the panel closes (TF-19)")
	void turfChangedHands_refusedAndClosed() {
		when(access.denyKey(viewer, session)).thenReturn("Quartermaster.Owner_Changed");

		view.attemptBuy(flow, viewer, session);

		verify(flow).end();
		assertUnpaid();
	}

	@Test
	@DisplayName("an ally sees the look-only line on the buy item, not the buy action")
	void allyLore_isLookOnly() {
		Gang owner = mock(Gang.class);
		when(owner.getId()).thenReturn(1);
		Gang ally = mock(Gang.class);
		when(ally.getId()).thenReturn(2);
		TurfPowerupFlowSession allySession =
				new TurfPowerupFlowSession(session.getTurf(), owner, ally, "Quartermaster");

		assertEquals(QuartermasterAccess.ALLIES_LOOK, view.clickLine(allySession, true));
		assertEquals("Quartermaster.Garrison_Buy_Click", view.clickLine(session, true));
	}

	/** The real EconomyHandler keeps the balance, so the bank paid exactly {@code price} when it dropped by that much. */
	private void assertPaid(String price) {
		assertEquals(0, new BigDecimal("50000").subtract(new BigDecimal(price)).compareTo(bank.getAmount()));
	}

	private void assertUnpaid() {
		assertEquals(0, new BigDecimal("50000").compareTo(bank.getAmount()));
	}
}
