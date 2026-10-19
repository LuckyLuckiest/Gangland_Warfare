package org.luckyraven.gangland.copsncrooks.npc.police.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 0.21.0 wave 2 fnd-w2: the Most_Wanted announcement is never swallowed by the radio gap. */
@DisplayName("YamlCopConfigProvider - Most_Wanted dispatch priority")
class YamlCopConfigProviderMostWantedTest {

	@Test
	@DisplayName("DISPATCH_PRIORITY gains Most_Wanted and keeps its four earlier entries")
	void dispatchPriority_hasMostWanted() {
		assertTrue(YamlCopConfigProvider.DISPATCH_PRIORITY.contains("Most_Wanted"));
		assertTrue(YamlCopConfigProvider.DISPATCH_PRIORITY.containsAll(
				java.util.Set.of("Dispatch_En_Route", "Wipe_Refill", "Handoff", "Post_Up")));
	}
}
