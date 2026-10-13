package org.luckyraven.gangland.turf.npc.view;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
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
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderConfig;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.support.TurfTextFixture;
import org.luckyraven.keystone.inventory.flow.MenuFlow;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
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
	private IntUnaryOperator          aliveGuards;
	private EconomyHandler            bank;
	private MenuFlow<TurfPowerupFlowSession> flow;
	private Player                    viewer;
	private TurfPowerupFlowSession    session;
	private TurfPowerupGarrisonView   view;
	private TurfMessages              messages;
	private AtomicReference<TurfDefenderConfig> waves;

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	@BeforeEach
	void setUp() {
		garrisons = mock(GarrisonManager.class);
		registry  = new PowerupRegistry();
		registry.setGarrisonCost(BigDecimal.valueOf(1000));
		buffs  = mock(ActiveBuffManager.class);
		access = mock(QuartermasterAccess.class);
		rules  = mock(TurfRules.class);
		aliveGuards = mock(IntUnaryOperator.class);
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
		when(messages.text(anyString(), anyString(), any(Object[].class))).thenAnswer(invocation -> invocation.getArgument(0));
		waves = new AtomicReference<>(new TurfDefenderConfig("turf_defender", 32.0, 600));
		view = new TurfPowerupGarrisonView(garrisons, registry, buffs, access, rules,
		                                   aliveGuards, waves::get, messages, "STONE", "&7");
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
	@DisplayName("guards still in the field count against the reserve: stock 0 + 12 alive is refused (r2)")
	void reserveCap_countsDeployedGuards() {
		when(garrisons.count(TURF_ID)).thenReturn(0);
		when(aliveGuards.applyAsInt(TURF_ID)).thenReturn(12);

		view.attemptBuy(flow, viewer, session);

		assertUnpaid();
		verify(garrisons, never()).add(anyInt(), anyInt());
	}

	@Test
	@DisplayName("a buy is allowed while stock plus deployed guards is under Max_Reserve")
	void reserveCap_underCapStillBuys() {
		when(garrisons.count(TURF_ID)).thenReturn(4);
		when(aliveGuards.applyAsInt(TURF_ID)).thenReturn(7);

		view.attemptBuy(flow, viewer, session);

		assertPaid("1000");
		verify(garrisons).add(TURF_ID, 1);
	}
	@Test
	@DisplayName("a discount below the 10% floor still costs 10% of Cost_Per_Guard (T-275)")
	void price_discountBelowFloor_isTenPercent() {
		when(buffs.effectiveMultiplier(TURF_ID, EffectType.GARRISON_DISCOUNT)).thenReturn(0.0);

		assertEquals(0, new BigDecimal("100").compareTo(view.price(TURF_ID)));
	}

	@Test
	@DisplayName("a negative discount multiplier costs 10% of Cost_Per_Guard (T-275)")
	void price_negativeMultiplier_isTenPercent() {
		when(buffs.effectiveMultiplier(TURF_ID, EffectType.GARRISON_DISCOUNT)).thenReturn(-2.0);

		assertEquals(0, new BigDecimal("100").compareTo(view.price(TURF_ID)));
	}

	@Test
	@DisplayName("stacked surcharges never raise the price above Cost_Per_Guard (T-275)")
	void price_surcharge_isFullPrice() {
		when(buffs.effectiveMultiplier(TURF_ID, EffectType.GARRISON_DISCOUNT)).thenReturn(1.5);

		assertEquals(0, new BigDecimal("1000").compareTo(view.price(TURF_ID)));
	}

	@Test
	@DisplayName("a NaN multiplier falls back to the full price instead of throwing (T-275)")
	void price_nanMultiplier_isFullPrice() {
		when(buffs.effectiveMultiplier(TURF_ID, EffectType.GARRISON_DISCOUNT)).thenReturn(Double.NaN);

		assertEquals(0, new BigDecimal("1000").compareTo(view.price(TURF_ID)));
	}

	@Test
	@DisplayName("a guard never costs less than 1, however small Cost_Per_Guard is (T-275)")
	void price_neverBelowOne() {
		registry.setGarrisonCost(BigDecimal.valueOf(3));
		when(buffs.effectiveMultiplier(TURF_ID, EffectType.GARRISON_DISCOUNT)).thenReturn(0.1);

		assertEquals(0, BigDecimal.ONE.compareTo(view.price(TURF_ID)));
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

	@Test
	@DisplayName("the next wave is capped by Wave_Size, the stock and Max_Alive minus guards on duty (W7-H)")
	void nextWave_cappedByWaveStockAndMaxAlive() {
		TurfDefenderConfig config = new TurfDefenderConfig("turf_defender", 32.0, 600, 3, 6, 20, 8, 16);

		assertEquals(1, TurfPowerupGarrisonView.nextWave(1, 0, config));
		assertEquals(3, TurfPowerupGarrisonView.nextWave(10, 0, config));
		assertEquals(1, TurfPowerupGarrisonView.nextWave(10, 5, config));
		assertEquals(0, TurfPowerupGarrisonView.nextWave(10, 6, config));
		assertEquals(0, TurfPowerupGarrisonView.nextWave(0, 0, config));
	}

	@Test
	@DisplayName("the info lore shows guards on duty, the reserve against the cap and the next wave (T-822)")
	void infoLore_showsOnDutyReserveAndNextWave() {
		when(garrisons.count(TURF_ID)).thenReturn(4);
		when(aliveGuards.applyAsInt(TURF_ID)).thenReturn(2);
		when(rules.getMaxReserve()).thenReturn(12);

		List<String> lore = view.infoLore(session.getTurf(), session.getOwnerGang());

		assertTrue(lore.contains("Quartermaster.Garrison_On_Duty"));
		assertTrue(lore.contains("Quartermaster.Garrison_Reserve"));
		assertTrue(lore.contains("Quartermaster.Garrison_Next_Wave"));
		verify(messages).text(eq("Quartermaster.Garrison_Reserve"), anyString(), eq("count"), eq(6), eq("max"), eq(12));
		verify(messages).text(eq("Quartermaster.Garrison_Next_Wave"), anyString(), eq("count"), eq(3));
	}

	@Test
	@DisplayName("the next-wave line reads the live turf_npcs.yml Garrison knobs, not a snapshot (W7-H)")
	void infoLore_readsLiveConfig() {
		when(garrisons.count(TURF_ID)).thenReturn(4);
		waves.set(new TurfDefenderConfig("turf_defender", 32.0, 600, 1, 6, 20, 8, 16));

		view.infoLore(session.getTurf(), session.getOwnerGang());

		verify(messages).text(eq("Quartermaster.Garrison_Next_Wave"), anyString(), eq("count"), eq(1));
	}

	@Test
	@DisplayName("a full reserve tells the owner the cap instead of Click to add (T-823)")
	void buyLine_fullReserve_ownerSeesFull() {
		when(garrisons.count(TURF_ID)).thenReturn(10);
		when(aliveGuards.applyAsInt(TURF_ID)).thenReturn(2);

		assertEquals("Quartermaster.Garrison_Buy_Full", view.buyLine(session, true));
	}

	@Test
	@DisplayName("a reserve under the cap keeps the click line (T-823)")
	void buyLine_underCap_ownerSeesClick() {
		when(garrisons.count(TURF_ID)).thenReturn(3);
		when(aliveGuards.applyAsInt(TURF_ID)).thenReturn(0);

		assertEquals("Quartermaster.Garrison_Buy_Click", view.buyLine(session, true));
	}

	@Test
	@DisplayName("an ally keeps the look-only line even on a full reserve (T-823, D25=B)")
	void buyLine_fullReserve_allyStillLookOnly() {
		Gang owner = mock(Gang.class);
		when(owner.getId()).thenReturn(1);
		Gang ally = mock(Gang.class);
		when(ally.getId()).thenReturn(2);
		TurfPowerupFlowSession allySession =
				new TurfPowerupFlowSession(session.getTurf(), owner, ally, "Quartermaster");
		when(garrisons.count(TURF_ID)).thenReturn(12);

		assertEquals(QuartermasterAccess.ALLIES_LOOK, view.buyLine(allySession, true));
	}

	@Test
	@DisplayName("the view's fallback literals equal the shipped turf_messages.yml lines (W7-H)")
	void fallbacksEqualShippedYaml() throws Exception {
		try (var settings = TurfTextFixture.stubMoneySymbol()) {
			TurfMessageContract legacy = mock(TurfMessageContract.class);
			Path shippedDir = Files.createDirectories(tempDir.resolve("shipped"));
			Path emptyDir = Files.createDirectories(tempDir.resolve("empty"));
			TurfMessages shipped = TurfTextFixture.messages(shippedDir, legacy);
			TurfMessages empty = TurfTextFixture.emptyMessages(emptyDir, legacy);
			String[][] keys = {
					{"Quartermaster.Garrison_On_Duty", "&7On duty: &f%count%"},
					{"Quartermaster.Garrison_Reserve", "&7Reserve: &f%count%&7/&f%max% &8(stock and on duty)"},
					{"Quartermaster.Garrison_Next_Wave", "&7Next wave: up to &f%count% &7guards, no extra cost"},
					{"Quartermaster.Garrison_Buy_Full", "&cReserve full: &f%max% &cguards."}
			};
			for (String[] key : keys) {
				assertEquals(empty.text(key[0], key[1]), shipped.text(key[0], key[1]), key[0]);
			}
		}
	}

	/** The real EconomyHandler keeps the balance, so the bank paid exactly {@code price} when it dropped by that much. */
	private void assertPaid(String price) {
		assertEquals(0, new BigDecimal("50000").subtract(new BigDecimal(price)).compareTo(bank.getAmount()));
	}

	private void assertUnpaid() {
		assertEquals(0, new BigDecimal("50000").compareTo(bank.getAmount()));
	}
}
