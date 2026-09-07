package org.luckyraven.gangland.config;

import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.data.economy.BankTiers;
import org.luckyraven.gangland.data.economy.GanglandMoneyDropClassifier;
import org.luckyraven.gangland.gang.wanted.WantedKillTrackers;
import org.luckyraven.gangland.turf.turfnpcs.TurfNpcContracts;
import org.luckyraven.keystone.bean.Bean;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins the registration type of the four core holder seams a runtime module installs a delegate into.
 *
 * <p>Keystone's {@code DependencyContainer.registerInstance(type, instance)} files a bean under the {@code @Bean}
 * method's <em>declared</em> return type plus the concrete class's supertypes — never under the concrete class
 * itself. A holder declared as its interface (e.g. {@code MoneyDropClassifier}) is therefore invisible to
 * {@code container.getInstance(GanglandMoneyDropClassifier.class)}, and the module's {@code @PostConstruct}
 * install hook throws at boot whenever the module is present. Found by the flip-1 code review (2026-09-07).
 */
class HolderSeamBeanTypeTest {

	@Test
	void moneyDropClassifierBeanIsDeclaredAsTheHolderClass() {
		assertDeclaredReturnType(DataConfig.class, "moneyDropClassifier", GanglandMoneyDropClassifier.class);
	}

	@Test
	void bankTiersBeanIsDeclaredAsTheHolderClass() {
		assertDeclaredReturnType(DataConfig.class, "bankTiers", BankTiers.class);
	}

	@Test
	void wantedKillTrackersBeanIsDeclaredAsTheHolderClass() {
		assertDeclaredReturnType(DataConfig.class, "wantedKillTrackers", WantedKillTrackers.class);
	}

	@Test
	void turfNpcContractsBeanIsDeclaredAsTheHolderClass() {
		assertDeclaredReturnType(TurfConfig.class, "turfNpcContracts", TurfNpcContracts.class);
	}

	private static void assertDeclaredReturnType(Class<?> configuration, String beanMethod, Class<?> holder) {
		Method method = findBeanMethod(configuration, beanMethod);
		assertNotNull(method, configuration.getSimpleName() + "." + beanMethod + " must exist and be a @Bean");
		assertEquals(holder, method.getReturnType(),
		             configuration.getSimpleName() + "." + beanMethod + " must declare the concrete holder as its "
		             + "return type: the container registers a bean under the declared type and the concrete "
		             + "class's supertypes only, so a module looking the holder up by its class would get null");
	}

	private static Method findBeanMethod(Class<?> configuration, String name) {
		for (Method method : configuration.getDeclaredMethods()) {
			if (method.getName().equals(name) && method.isAnnotationPresent(Bean.class)) {
				return method;
			}
		}
		return null;
	}
}
