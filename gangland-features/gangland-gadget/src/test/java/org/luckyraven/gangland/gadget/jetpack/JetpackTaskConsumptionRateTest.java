package org.luckyraven.gangland.gadget.jetpack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gadget.config.GadgetPhysicsConfig;
import org.luckyraven.gangland.gadget.fuel.FuelService;
import org.luckyraven.gangland.item.wearable.Wearable;
import org.luckyraven.gangland.item.wearable.WearableTrait;
import org.luckyraven.gangland.weapon.WeaponService;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Pins {@code JetpackTask.getEffectiveConsumptionRate}'s {@code WearableTrait.FUEL_EFFICIENT} discount math
 * (gadgets-cars-fuel-jetpack.md — Test Surface bullet "JetpackTask.getEffectiveConsumptionRate — FUEL_EFFICIENT
 * levels 0/1/2/overflow, floor of 1").
 *
 * <p>The method is {@code private}, so this test reaches it via reflection rather than driving the full
 * {@code run()}/{@code applyVerticalPhysics} tick pipeline (which needs a live {@code Player}, session and fuel
 * service wired end-to-end) — the audit itself flags this method as "worth promoting to package-private for
 * testability"; reflection avoids touching production code to get there.
 */
@DisplayName("JetpackTask.getEffectiveConsumptionRate — FUEL_EFFICIENT discount")
class JetpackTaskConsumptionRateTest {

	private static int effectiveRate(Wearable jetpack) throws Exception {
		JetpackTask task = new JetpackTask(mock(JetpackSession.class), mock(JetpackService.class),
		                                   mock(FuelService.class), mock(GadgetPhysicsConfig.class),
		                                   mock(WeaponService.class));
		Method method = JetpackTask.class.getDeclaredMethod("getEffectiveConsumptionRate", Wearable.class);
		method.setAccessible(true);
		return (int) method.invoke(task, jetpack);
	}

	private static Wearable jetpackWithTrait(int baseRate, Integer fuelEfficientLevel) {
		Wearable.WearableBuilder builder = Wearable.builder()
		                                            .wearableKey("jetpack")
		                                            .fuelConsumptionRate(baseRate);
		if (fuelEfficientLevel != null) {
			builder.traits(Map.of(WearableTrait.FUEL_EFFICIENT, fuelEfficientLevel));
		} else {
			builder.traits(Map.of());
		}
		return builder.build();
	}

	@Test
	@DisplayName("level 0 (or absent) FUEL_EFFICIENT trait leaves the base rate unchanged")
	void level0_noDiscount() throws Exception {
		assertEquals(10, effectiveRate(jetpackWithTrait(10, 0)));
		assertEquals(10, effectiveRate(jetpackWithTrait(10, null)));
	}

	@Test
	@DisplayName("level 1 applies a 10% discount (0.10 effectPerLevel)")
	void level1_tenPercentDiscount() throws Exception {
		// 10 * (1 - 0.10) = 9.0 -> (int) 9
		assertEquals(9, effectiveRate(jetpackWithTrait(10, 1)));
	}

	@Test
	@DisplayName("level 2 (the trait's maxLevel) applies a 20% discount")
	void level2_twentyPercentDiscount() throws Exception {
		// 10 * (1 - 0.20) = 8.0 -> (int) 8
		assertEquals(8, effectiveRate(jetpackWithTrait(10, 2)));
	}

	@Test
	@DisplayName("a level above maxLevel (3) is capped at level 2's 20% discount, not applied uncapped")
	void levelAboveMax_cappedAtMaxLevel() throws Exception {
		// WearableTrait.FUEL_EFFICIENT.maxLevel == 2, so level 3 is capped to 2 -> same as the level-2 case.
		assertEquals(8, effectiveRate(jetpackWithTrait(10, 3)));
	}

	@Test
	@DisplayName("the discounted rate floors at 1, never reaches or drops below 0")
	void discount_floorsAtOne() throws Exception {
		// baseRate=1, 20% discount -> 1*(1-0.2)=0.8 -> (int) 0 -> Math.max(1, 0) = 1
		assertEquals(1, effectiveRate(jetpackWithTrait(1, 2)));
	}

}
