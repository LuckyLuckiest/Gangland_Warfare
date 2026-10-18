package org.luckyraven.gangland.npcshops.config;

import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.data.economy.HotCashRouter;
import org.luckyraven.keystone.bean.Bean;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The hold-up payout shares the core's single {@link HotCashRouter} bean (container param = wiring edge). */
class TraderModuleConfigRouterWiringTest {

	@Test
	void holdUpServiceBeanTakesTheSharedRouter() {
		Method method = null;
		for (Method candidate : TraderModuleConfig.class.getDeclaredMethods()) {
			if (candidate.getName().equals("holdUpService") && candidate.isAnnotationPresent(Bean.class)) {
				method = candidate;
			}
		}

		assertNotNull(method, "TraderModuleConfig.holdUpService must exist and be a @Bean");
		assertTrue(Arrays.asList(method.getParameterTypes()).contains(HotCashRouter.class),
		           "the hold-up payout must receive the shared HotCashRouter bean");
	}

}
