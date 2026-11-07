package org.luckyraven.gangland.turf.npc.defender;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.config.TurfNpcsConfigLoader;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.EffectType;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.gangland.turf.powerups.PowerupRegistry;
import org.luckyraven.gangland.turf.support.InMemoryGarrisonRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GarrisonRoster - a turf's garrison and its live, buffed guard numbers")
class GarrisonRosterTest {

	private TurfManager       turfs;
	private GarrisonManager   garrisons;
	private ActiveBuffManager buffs;
	private GarrisonRoster    roster;

	@BeforeEach
	void setUp() {
		turfs     = mock(TurfManager.class);
		garrisons = new GarrisonManager(new InMemoryGarrisonRepository());
		buffs     = mock(ActiveBuffManager.class);
		TurfNpcsConfigLoader loader = mock(TurfNpcsConfigLoader.class);
		when(loader.getDefenderConfig()).thenReturn(new TurfDefenderConfig("turf_defender", 32.0, 3, 6, 20, 8, 16));
		roster = new GarrisonRoster(turfs, garrisons, loader, mock(GangLookupContract.class), mock(TurfMessages.class),
		                            new PowerupRegistry(), buffs);
		when(buffs.effectiveMultiplier(anyInt(), org.mockito.ArgumentMatchers.any())).thenReturn(1.0);
	}

	private void buff(EffectType type, double m) {
		when(buffs.effectiveMultiplier(eq(1), eq(type))).thenReturn(m);
	}

	private Turf ownedTurf() {
		Turf turf = new Turf(1, "Turf", new CuboidRegion("world", 0, 0, 40, 40), 7, BigDecimal.TEN, 0L, 0L);
		when(turfs.get(1)).thenReturn(turf);
		return turf;
	}

	@Test
	@DisplayName("Bigger Waves and Faster Reinforcements scale wave, max and cooldown, clamped at the limits")
	void configOf_appliesWavesAndCooldown_clamped() {
		buff(EffectType.BIGGER_GUARD_WAVES, 1.5);
		assertEquals(5, roster.configOf(1).waveSize());
		assertEquals(9, roster.configOf(1).maxAlive());

		buff(EffectType.BIGGER_GUARD_WAVES, 5.0);
		assertEquals(6, roster.configOf(1).waveSize(), "clamped at Max_Guard_Waves 2.0");
		assertEquals(12, roster.configOf(1).maxAlive());

		buff(EffectType.FASTER_REINFORCEMENTS, 0.5);
		assertEquals(10, roster.configOf(1).waveCooldownSeconds());

		buff(EffectType.FASTER_REINFORCEMENTS, 0.1);
		assertEquals(5, roster.configOf(1).waveCooldownSeconds(), "floored at Min_Reinforcement_Wait 0.25");
	}

	@Test
	@DisplayName("Bigger Waves applies only in a contest: Standing_Cap is not scaled")
	void configOf_doesNotScaleStandingCap() {
		buff(EffectType.BIGGER_GUARD_WAVES, 2.0);

		assertEquals(12, roster.configOf(1).maxAlive());
		assertEquals(5, roster.configOf(1).standingCap());
	}

	@Test
	@DisplayName("Bigger Waves never raises the peace target when Max_Alive is below Standing_Cap")
	void configOf_buffNeverRaisesPeaceTarget() {
		TurfNpcsConfigLoader loader = mock(TurfNpcsConfigLoader.class);
		when(loader.getDefenderConfig()).thenReturn(new TurfDefenderConfig("turf_defender", 32.0, 3, 3, 20, 8, 16,
				GarrisonComposition.NONE, 0.5, 12, 6.0, 5));
		GarrisonRoster small = new GarrisonRoster(turfs, garrisons, loader, mock(GangLookupContract.class),
		                                          mock(TurfMessages.class), new PowerupRegistry(), buffs);
		buff(EffectType.BIGGER_GUARD_WAVES, 2.0);

		assertEquals(6, small.configOf(1).maxAlive());
		assertEquals(3, TurfGarrison.target(10, false, small.configOf(1)));
	}

	@Test
	@DisplayName("garrisonOf records the owner and today's discounted guard price, clamped like the panel's")
	void garrisonOf_setsFundedPrice() {
		ownedTurf();
		buff(EffectType.GARRISON_DISCOUNT, 0.5);

		TurfGarrison garrison = roster.garrisonOf(1);

		assertNotNull(garrison);
		assertEquals(7, garrison.ownerGangId);
		assertEquals(500, garrison.guardPrice);

		buff(EffectType.GARRISON_DISCOUNT, 0.01);
		assertEquals(100, roster.garrisonOf(1).guardPrice, "the discount stops at 0.1");
	}

	@Test
	@DisplayName("garrisonOf prices a guard at full cost for a non-finite or above-1 discount and rounds half up (T-275)")
	void garrisonOf_fundedPrice_nonFiniteAndCapAndRounding() {
		ownedTurf();

		buff(EffectType.GARRISON_DISCOUNT, Double.NaN);
		assertEquals(1000, roster.garrisonOf(1).guardPrice, "NaN means no discount");
		buff(EffectType.GARRISON_DISCOUNT, Double.POSITIVE_INFINITY);
		assertEquals(1000, roster.garrisonOf(1).guardPrice, "infinity means no discount");
		buff(EffectType.GARRISON_DISCOUNT, Double.NEGATIVE_INFINITY);
		assertEquals(1000, roster.garrisonOf(1).guardPrice, "negative infinity means no discount");
		buff(EffectType.GARRISON_DISCOUNT, 5.0);
		assertEquals(1000, roster.garrisonOf(1).guardPrice, "a multiplier above 1 never raises the price");
		buff(EffectType.GARRISON_DISCOUNT, 0.1234);
		assertEquals(123, roster.garrisonOf(1).guardPrice, "123.4 rounds down");
		buff(EffectType.GARRISON_DISCOUNT, 0.1235);
		assertEquals(124, roster.garrisonOf(1).guardPrice, "123.5 rounds half up");
	}

	@Test
	@DisplayName("garrisonOf reads the live row: owned follows the count and spend consumes it")
	void garrisonOf_ownedIsTheRow() {
		ownedTurf();
		garrisons.add(1, 4);

		TurfGarrison garrison = roster.garrisonOf(1);
		assertEquals(4, garrison.owned());

		garrison.spend(1);
		assertEquals(3, garrisons.count(1));

		garrisons.add(1, 2);
		assertEquals(5, garrison.owned());
	}

	@Test
	@DisplayName("garrisonOf is null for a gone turf and for an unclaimed one")
	void garrisonOf_nullForUnclaimed() {
		assertNull(roster.garrisonOf(1), "gone");

		Turf unclaimed = new Turf(1, "Turf", new CuboidRegion("world", 0, 0, 40, 40), null, BigDecimal.TEN, 0L, 0L);
		when(turfs.get(1)).thenReturn(unclaimed);

		assertNull(roster.garrisonOf(1), "unclaimed");
	}

	@Test
	@DisplayName("guardHealth is the Toughness multiplier, clamped; NaN and below-1 are no buff")
	void guardHealth_clamped_nanIsOne() {
		buff(EffectType.GUARD_TOUGHNESS, 1.5);
		assertEquals(1.5, roster.guardHealth(1));

		buff(EffectType.GUARD_TOUGHNESS, 5.0);
		assertEquals(3.0, roster.guardHealth(1), "clamped at Max_Guard_Toughness 3.0");

		buff(EffectType.GUARD_TOUGHNESS, Double.NaN);
		assertEquals(1.0, roster.guardHealth(1));

		buff(EffectType.GUARD_TOUGHNESS, 0.5);
		assertEquals(1.0, roster.guardHealth(1));
	}

	@Test
	@DisplayName("stockedTurfIds lists the rows with stock")
	void stockedTurfIds_fromTheManager() {
		garrisons.add(3, 2);

		assertEquals(List.of(3), roster.stockedTurfIds());
	}
}
