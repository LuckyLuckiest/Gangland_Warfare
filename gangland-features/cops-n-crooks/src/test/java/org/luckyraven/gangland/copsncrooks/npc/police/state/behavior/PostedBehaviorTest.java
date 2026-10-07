package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/** {@link PostedBehavior}: a posted cop only keeps its post; leaving the state gives the post up. */
@DisplayName("PostedBehavior")
class PostedBehaviorTest {

	private final PostedBehavior behavior = new PostedBehavior();

	@Test
	@DisplayName("each tick holds the post, with no transition (no leash give-up, no cuffing)")
	void tick_ticksThePostAndNothingElse() {
		CopNpc cop = mock(CopNpc.class);

		behavior.tick(cop);

		verify(cop).tickPost();
		verify(cop, never()).transitionTo(any());
	}

	@Test
	@DisplayName("onExit releases the post")
	void onExit_releasesThePost() {
		CopNpc cop = mock(CopNpc.class);

		behavior.onExit(cop);

		verify(cop).releasePost();
	}

	@Test
	@DisplayName("onEnter leaves the cop alone: the controller already gave it its post")
	void onEnter_doesNothing() {
		CopNpc cop = mock(CopNpc.class);

		behavior.onEnter(cop);

		verifyNoMoreInteractions(cop);
	}
}
