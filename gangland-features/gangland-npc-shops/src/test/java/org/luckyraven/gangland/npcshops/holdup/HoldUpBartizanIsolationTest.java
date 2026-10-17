package org.luckyraven.gangland.npcshops.holdup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BartizanReferenceScan;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.gangland.npcshops.config.TraderModuleConfig;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * npc-shops is Bartizan-SOFT (no Plugins: entry): the module must load without Bartizan, so exactly one class may
 * name a Bartizan type, and the hold-up service must be wired as a bean in the trader configuration.
 */
@DisplayName("Hold-ups - soft Bartizan isolation and bean wiring")
class HoldUpBartizanIsolationTest {

	@Test
	@DisplayName("only BartizanAim references Bartizan anywhere in its compiled constant pool")
	void onlyBartizanAimReferencesBartizan() throws IOException {
		Set<String> found = BartizanReferenceScan.findBartizanReferencingClasses(Path.of("target/classes"));
		assertEquals(Set.of("org.luckyraven.gangland.npcshops.holdup.BartizanAim"), found);
	}

	@Test
	@DisplayName("TraderModuleConfig produces a HoldUpService bean and no @Bean signature names a Bartizan type")
	void holdUpServiceIsABean() {
		boolean hasBean = Arrays.stream(TraderModuleConfig.class.getDeclaredMethods())
		                        .anyMatch(m -> m.isAnnotationPresent(Bean.class)
		                                       && m.getReturnType() == HoldUpService.class);
		assertTrue(hasBean, "TraderModuleConfig needs a @Bean returning HoldUpService");

		for (Method m : TraderModuleConfig.class.getDeclaredMethods()) {
			assertTrue(Arrays.stream(m.getParameterTypes()).noneMatch(t -> t.getName().contains("bartizan")),
			           m + " must not take a Bartizan type");
		}
	}

}
