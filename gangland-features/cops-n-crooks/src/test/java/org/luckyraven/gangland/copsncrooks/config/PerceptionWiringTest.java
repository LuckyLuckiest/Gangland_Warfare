package org.luckyraven.gangland.copsncrooks.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.perch.OverwatchController;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.perimeter.PerimeterController;
import org.luckyraven.gangland.copsncrooks.npc.police.standoff.StandoffController;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.mockito.MockedConstruction;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

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

	@Test
	@DisplayName("the copSpawnManager bean hands that very CopPerception to the manager it builds (perception is the LAST parameter)")
	void spawnManagerBeanHandsPerceptionToManager() throws Exception {
		Method spawnBean = beans().stream().filter(method -> method.getReturnType() == CopSpawnManager.class)
		                          .findFirst().orElseThrow();
		Class<?>[] types = spawnBean.getParameterTypes();
		assertEquals(CopPerception.class, types[types.length - 1], "CopPerception must be the last bean parameter");
		Object[] args = new Object[types.length];
		for (int i = 0; i < types.length; i++)
			args[i] = mock(types[i]);
		CopsNCrooksModuleConfig config = new CopsNCrooksModuleConfig(mock(JavaPlugin.class),
		                                                             mock(DependencyContainer.class));

		try (MockedConstruction<CopSpawnManager> built = mockConstruction(CopSpawnManager.class)) {
			spawnBean.invoke(config, args);

			verify(built.constructed().get(0)).setPerception((CopPerception) args[args.length - 1]);
		}
	}

	@Test
	@DisplayName("the perimeter, overwatch and standoff beans take CopPerception and hand its lock-on filters to the controller (row 333)")
	void postsAskTheLockOnFilters() throws Exception {
		assertPostsAsk(EvasionModuleConfig.class, PerimeterController.class, PerimeterController::setLockOn);
		assertPostsAsk(OverwatchModuleConfig.class, OverwatchController.class, OverwatchController::setLockOn);
		assertPostsAsk(StandoffModuleConfig.class, StandoffController.class, StandoffController::setLockOn);
	}

	@SuppressWarnings("unchecked")
	private static <T> void assertPostsAsk(Class<?> configType, Class<T> controllerType,
	                                       BiConsumer<T, BiPredicate<CopNpc, Player>> setLockOn) throws Exception {
		Method bean = Arrays.stream(configType.getDeclaredMethods())
		                    .filter(method -> method.isAnnotationPresent(Bean.class))
		                    .filter(method -> method.getReturnType() == controllerType).findFirst().orElseThrow();
		Class<?>[] types = bean.getParameterTypes();
		assertTrue(Arrays.asList(types).contains(CopPerception.class), controllerType.getSimpleName());
		Object[] args = new Object[types.length];
		for (int i = 0; i < types.length; i++)
			args[i] = mock(types[i]);
		CopPerception perception = (CopPerception) args[Arrays.asList(types).indexOf(CopPerception.class)];
		when(perception.allowsLockOn(any(CopNpc.class), any(Player.class))).thenReturn(false);
		Object config = configType.getConstructor(JavaPlugin.class, DependencyContainer.class)
		                          .newInstance(mock(JavaPlugin.class), mock(DependencyContainer.class));

		try (MockedConstruction<T> built = mockConstruction(controllerType)) {
			bean.invoke(config, args);

			org.mockito.ArgumentCaptor<BiPredicate<CopNpc, Player>> lockOn = org.mockito.ArgumentCaptor.forClass(
					BiPredicate.class);
			setLockOn.accept(verify(built.constructed().get(0)), lockOn.capture());
			assertFalse(lockOn.getValue().test(mock(CopNpc.class), mock(Player.class)));
		}
	}
}
