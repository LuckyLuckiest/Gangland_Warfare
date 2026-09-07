package org.luckyraven.gangland.turf;

import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.turfnpcs.TurfNpcContracts;
import org.luckyraven.keystone.bean.Bean;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins the registration type of the turf → NPC holder seam the cops-n-crooks module installs a delegate into.
 *
 * <p>Keystone's {@code DependencyContainer.registerInstance(type, instance)} files a bean under the {@code @Bean}
 * method's <em>declared</em> return type plus the concrete class's supertypes — never under the concrete class
 * itself. Moved out of {@code gangland-impl}'s {@code HolderSeamBeanTypeTest} in the 0.8.4 turf flip (group C, T7)
 * when {@code TurfConfig} was deleted from core and its beans moved verbatim into {@code TurfModuleConfig} — core
 * test code may not name a module type.
 */
class TurfModuleConfigHolderSeamTest {

	@Test
	void turfNpcContractsBeanIsDeclaredAsTheHolderClass() {
		Method method = findBeanMethod(TurfModuleConfig.class, "turfNpcContracts");
		assertNotNull(method, "TurfModuleConfig.turfNpcContracts must exist and be a @Bean");
		assertEquals(TurfNpcContracts.class, method.getReturnType(),
		             "TurfModuleConfig.turfNpcContracts must declare the concrete holder as its return type: the "
		             + "container registers a bean under the declared type and the concrete class's supertypes "
		             + "only, so a module looking the holder up by its class would get null");
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
