package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehavior;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The 0.20 seam ships PATROL, WARNING and OVERWATCH as empty skeletons so the behaviour map has no hole; the owning
 * lanes fill them in but a skeleton must never attack, navigate or change state on its own.
 */
@DisplayName("Skeleton behaviours (PATROL, WARNING, OVERWATCH) do nothing")
class SkeletonBehaviorsTest {

	private static final List<CopBehavior> SKELETONS = List.of(new PatrolBehavior(), new WarningBehavior());

	@Test
	@DisplayName("enter, tick and exit leave the cop untouched")
	void lifecycleDoesNothing() {
		for (CopBehavior behavior : SKELETONS) {
			CopNpc cop = mock(CopNpc.class);

			behavior.onEnter(cop);
			behavior.tick(cop);
			behavior.onExit(cop);

			verifyNoInteractions(cop);
		}
	}
}
