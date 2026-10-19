package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.21.0 fnd-squad: the five feature lanes (surrender, rescue, standoff, breach, pursuit report) each own an empty
 * {@code @Configuration} that they fill with beans, so no lane edits {@code CopsNCrooksModule}. Here they are empty:
 * annotated, constructed from (JavaPlugin, DependencyContainer) like their siblings (lanes add their own beans later).
 */
@DisplayName("0.21.0 feature module configurations - empty @Configuration shells")
class FeatureModuleConfigShapeTest {

	private static final List<Class<?>> CONFIGS = List.of(SurrenderModuleConfig.class, RescueModuleConfig.class,
	                                                      StandoffModuleConfig.class, BreachModuleConfig.class,
	                                                      PursuitReportModuleConfig.class,
	                                                      GetawayModuleConfig.class, MostWantedModuleConfig.class);

	@Test
	@DisplayName("each is annotated @Configuration so the bean framework scans it")
	void annotatedConfiguration() {
		for (Class<?> type : CONFIGS)
			assertTrue(type.isAnnotationPresent(Configuration.class), type.getSimpleName() + " lacks @Configuration");
	}

	@Test
	@DisplayName("each has the (JavaPlugin, DependencyContainer) constructor so feature lanes can add beans without touching the module")
	void hasPluginAndContainerConstructor() {
		for (Class<?> type : CONFIGS)
			assertDoesNotThrow(() -> type.getConstructor(JavaPlugin.class, DependencyContainer.class),
			                   type.getSimpleName() + " lacks a (JavaPlugin, DependencyContainer) constructor");
	}

	@Test
	@DisplayName("each can be constructed from mocks")
	void constructsFromMocks() {
		for (Class<?> type : CONFIGS)
			assertDoesNotThrow(() -> type.getConstructor(JavaPlugin.class, DependencyContainer.class)
			                             .newInstance(org.mockito.Mockito.mock(JavaPlugin.class),
			                                          org.mockito.Mockito.mock(DependencyContainer.class)),
			                   type.getSimpleName());
	}
}
