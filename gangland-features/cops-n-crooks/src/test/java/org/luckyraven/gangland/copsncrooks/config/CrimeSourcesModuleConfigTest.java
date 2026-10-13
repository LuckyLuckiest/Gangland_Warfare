package org.luckyraven.gangland.copsncrooks.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.wanted.trespass.TrespassTracker;
import org.luckyraven.keystone.bean.Bean;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 0.20.0 crimes lane: {@link CrimeSourcesModuleConfig} produces the one {@link TrespassTracker} the listener injects. */
@DisplayName("CrimeSourcesModuleConfig")
class CrimeSourcesModuleConfigTest {

	@Test
	@DisplayName("exactly one @Bean method returns TrespassTracker")
	void oneTrespassTrackerBean() {
		long producers = Arrays.stream(CrimeSourcesModuleConfig.class.getDeclaredMethods())
		                       .filter(method -> method.isAnnotationPresent(Bean.class))
		                       .map(Method::getReturnType).filter(type -> type == TrespassTracker.class).count();

		assertEquals(1L, producers);
	}
}
