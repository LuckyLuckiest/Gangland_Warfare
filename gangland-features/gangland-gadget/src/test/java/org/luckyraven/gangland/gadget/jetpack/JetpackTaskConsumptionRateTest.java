package org.luckyraven.gangland.gadget.jetpack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gadget.config.GadgetPhysicsConfig;
import org.luckyraven.gangland.item.fuel.FuelService;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Pins {@code JetpackTask.getEffectiveConsumptionRate} post-S2 (WS7 G3): the fuel_efficient trait branch is
 * deleted outright (nothing in items/jetpacks.yml ever configured it, and Bartizan's stock jetpack never carried
 * the trait either — C1) — the method now returns the configured {@link Jetpack#getFuelConsumptionRate()}
 * verbatim. Re-pointed off Bartizan's deleted {@code Wearable} mock onto a plain {@link Jetpack}.
 */
@DisplayName("JetpackTask.getEffectiveConsumptionRate - post-S2, no trait branch")
class JetpackTaskConsumptionRateTest {

	private static int effectiveRate(Jetpack jetpack) throws Exception {
		JetpackTask task = new JetpackTask(mock(JetpackSession.class), mock(JetpackService.class),
		                                   mock(FuelService.class), mock(GadgetPhysicsConfig.class));
		Method method = JetpackTask.class.getDeclaredMethod("getEffectiveConsumptionRate", Jetpack.class);
		method.setAccessible(true);
		return (int) method.invoke(task, jetpack);
	}

	private static Jetpack jetpackWithRate(int rate) {
		return Jetpack.builder().jetpackId("jetpack").fuelConsumptionRate(rate).build();
	}

	@Test
	@DisplayName("returns the configured Fuel_Consumption_Rate verbatim - no trait discount exists any more")
	void returnsConfiguredRateVerbatim() throws Exception {
		assertEquals(10, effectiveRate(jetpackWithRate(10)));
		assertEquals(1, effectiveRate(jetpackWithRate(1)));
		assertEquals(0, effectiveRate(jetpackWithRate(0)));
	}
}
