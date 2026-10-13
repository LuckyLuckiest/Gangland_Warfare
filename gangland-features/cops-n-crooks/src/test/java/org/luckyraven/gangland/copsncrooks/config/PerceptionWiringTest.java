package org.luckyraven.gangland.copsncrooks.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.keystone.bean.Bean;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.20 seam: {@link CopsNCrooksModuleConfig} produces exactly one {@link CopPerception} bean, and the spawn-manager bean
 * depends on it (the bean-graph edge that makes lanes' {@code addSightFactor} calls land on the instance the cops use).
 */
@DisplayName("CopsNCrooksModuleConfig - CopPerception bean wiring")
class PerceptionWiringTest {

	private static List<Method> beans() {
		return Arrays.stream(CopsNCrooksModuleConfig.class.getDeclaredMethods())
		             .filter(method -> method.isAnnotationPresent(Bean.class)).toList();
	}

	@Test
	@DisplayName("exactly one @Bean method returns CopPerception")
	void oneCopPerceptionBean() {
		long producers = beans().stream().filter(method -> method.getReturnType() == CopPerception.class).count();

		assertEquals(1L, producers);
	}

	@Test
	@DisplayName("the copSpawnManager bean takes the CopPerception as a parameter")
	void spawnManagerBeanDependsOnPerception() {
		Method spawnBean = beans().stream().filter(method -> method.getReturnType() == CopSpawnManager.class)
		                          .findFirst().orElseThrow();

		assertTrue(Arrays.asList(spawnBean.getParameterTypes()).contains(CopPerception.class));
	}
}
