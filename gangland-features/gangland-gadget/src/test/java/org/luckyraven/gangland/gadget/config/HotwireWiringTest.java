package org.luckyraven.gangland.gadget.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gadget.car.theft.HotwireMessages;
import org.luckyraven.gangland.gadget.car.theft.HotwireService;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P-CRIME-1 wiring guard: without a HotwireService bean {@code CarEntityInteractListener} cannot be constructed and
 * the module fails to boot. The beans are checked by reflection so the test compiles before they exist.
 */
@DisplayName("Hotwire bean wiring")
class HotwireWiringTest {

	private static JavaPlugin plugin() {
		JavaPlugin plugin = mock(JavaPlugin.class);
		when(plugin.getName()).thenReturn("gangland");
		return plugin;
	}

	private static Object invokeBean(Object config, Class<?> returnType) throws Exception {
		Method bean = Arrays.stream(config.getClass().getMethods())
		                    .filter(m -> m.getReturnType() == returnType)
		                    .findFirst()
		                    .orElseThrow(() -> new AssertionError(
				                    config.getClass().getSimpleName() + " has no public @Bean returning "
				                    + returnType.getSimpleName()));
		assertNotNull(bean.getAnnotation(org.luckyraven.keystone.bean.Bean.class), "method must be @Bean");
		Object[] args = Arrays.stream(bean.getParameterTypes()).map(type -> mock(type)).toArray();
		try {
			return bean.invoke(config, args);
		} catch (InvocationTargetException e) {
			throw new AssertionError("bean method threw " + e.getCause(), e.getCause());
		}
	}

	@Test
	@DisplayName("gadgetModuleConfigProvidesHotwireService")
	void gadgetModuleConfigProvidesHotwireService() throws Exception {
		assertNotNull(invokeBean(new GadgetModuleConfig(plugin()), HotwireService.class));
	}

	@Test
	@DisplayName("gadgetFileConfigProvidesHotwireMessages")
	void gadgetFileConfigProvidesHotwireMessages() throws Exception {
		assertNotNull(invokeBean(new GadgetFileConfig(plugin()), HotwireMessages.class));
	}
}
