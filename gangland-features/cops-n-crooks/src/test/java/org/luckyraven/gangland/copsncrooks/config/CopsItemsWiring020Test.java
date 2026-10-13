package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.item.CopsItemVocabulary;
import org.luckyraven.gangland.copsncrooks.item.CopsItems;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.item.spi.ItemVocabulary;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lane f2-config (chunk C12): {@link CopsNCrooksModuleConfig} declares a {@link CopsItems} bean and a
 * {@link CopsItemVocabulary} bean that publishes the {@code copsncrooks} vocabulary on the {@code ServicesManager}. A
 * module configuration is registered before {@code installItemVocabularies}, so the publication is seen at boot. Found by
 * reflection so the test compiles against the skeleton.
 */
@DisplayName("CopsNCrooksModuleConfig - CopsItems and the item vocabulary")
class CopsItemsWiring020Test {

	private BukkitStatics            bukkit;
	private JavaPlugin               plugin;
	private CopLoader                copLoader;
	private CopsNCrooksModuleConfig  config;

	@BeforeEach
	void setUp() {
		bukkit    = BukkitStatics.install();
		plugin    = mock(JavaPlugin.class);
		when(plugin.getName()).thenReturn("gangland");
		copLoader = mock(CopLoader.class);
		config    = new CopsNCrooksModuleConfig(plugin, new DependencyContainer());
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("a @Bean method produces CopsItems from the plugin and the CopLoader")
	void copsItemsBean_isDeclared() throws Exception {
		Object bean = invokeBean(CopsItems.class, null);

		assertNotNull(bean);
		assertTrue(bean instanceof CopsItems);
	}

	@Test
	@DisplayName("a @Bean method publishes the copsncrooks vocabulary as an ItemVocabulary service of the plugin")
	void vocabularyBean_publishesService() throws Exception {
		CopsItems items = mock(CopsItems.class);

		Object bean = invokeBean(CopsItemVocabulary.class, items);

		assertNotNull(bean);
		verify(bukkit.servicesManager()).register(eq(ItemVocabulary.class), same((ItemVocabulary) bean), eq(plugin),
		                                          eq(ServicePriority.Normal));
	}

	private Object invokeBean(Class<?> returns, CopsItems items) throws Exception {
		Method method = Arrays.stream(CopsNCrooksModuleConfig.class.getDeclaredMethods())
		                      .filter(m -> m.isAnnotationPresent(Bean.class) && m.getReturnType() == returns)
		                      .findFirst().orElse(null);
		assertNotNull(method, "CopsNCrooksModuleConfig has no @Bean method returning " + returns.getSimpleName());

		Object[] args = new Object[method.getParameterCount()];
		Class<?>[] types = method.getParameterTypes();
		for (int i = 0; i < types.length; i++) {
			if (types[i].isAssignableFrom(JavaPlugin.class) || types[i] == JavaPlugin.class) args[i] = plugin;
			else if (types[i] == CopLoader.class) args[i] = copLoader;
			else if (types[i] == CopsItems.class) args[i] = items;
			else throw new AssertionError("unexpected bean parameter " + types[i].getSimpleName());
		}
		try {
			return method.invoke(config, args);
		} catch (InvocationTargetException exception) {
			if (exception.getCause() instanceof Exception cause) throw cause;
			throw exception;
		}
	}
}
