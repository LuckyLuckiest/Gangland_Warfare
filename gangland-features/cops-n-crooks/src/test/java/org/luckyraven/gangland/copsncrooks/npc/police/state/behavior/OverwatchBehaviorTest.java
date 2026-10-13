package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link OverwatchBehavior} (P-OVW-2, P-OVW-3): a perched Marksman holds his post and watches; he never chases or fires. */
@DisplayName("OverwatchBehavior")
class OverwatchBehaviorTest {

	private final OverwatchBehavior behavior = new OverwatchBehavior();
	private final CopNpc            cop      = mock(CopNpc.class);

	@Test
	@DisplayName("each tick keeps the post and never navigates toward a target")
	void holdsPostNoChase() {
		behavior.tick(cop);

		verify(cop).tickPost();
		verify(cop, never()).navigateTo(any());
		verify(cop, never()).tick(any());
	}

	@Test
	@DisplayName("with the target in view for 200 ticks, no attack of any kind")
	void neverAttacks() {
		when(cop.canSee(any(LivingEntity.class), anyDouble())).thenReturn(true);

		for (int i = 0; i < 200; i++) behavior.tick(cop);

		verify(cop, times(200)).tickPost();
		verify(cop, never()).attack(any());
		verify(cop, never()).attackEntity(any());
		verify(cop, never()).attemptCuff(any());
		verify(cop, never()).navigateTo(any());
	}

	@Test
	@DisplayName("leaving the state releases the post")
	void exitReleasesThePost() {
		behavior.onExit(cop);

		verify(cop).releasePost();
	}

	@Test
	@DisplayName("entering the state starts no walk and no attack")
	void enterDoesNotMoveOrAttack() {
		behavior.onEnter(cop);

		verify(cop, never()).navigateTo(any());
		verify(cop, never()).attack(any());
	}
}
