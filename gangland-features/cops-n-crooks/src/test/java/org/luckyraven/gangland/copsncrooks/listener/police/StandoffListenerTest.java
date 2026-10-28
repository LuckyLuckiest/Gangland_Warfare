package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.events.npc.CopDeathEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.standoff.StandoffController;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link StandoffListener}: a cop death is a casualty of its group target, at the spot where the cop fell. */
@DisplayName("StandoffListener")
class StandoffListenerTest {

	private final StandoffController controller = mock(StandoffController.class);
	private final StandoffListener   listener   = new StandoffListener(controller);
	private final CopNpc             cop        = mock(CopNpc.class);
	private final CopGroup           group      = mock(CopGroup.class);
	private final LivingEntity       body       = mock(LivingEntity.class);
	private final UUID               target     = UUID.randomUUID();
	private final Location           spot       = new Location(null, 5, 64, 7);

	StandoffListenerTest() {
		when(cop.getGroup()).thenReturn(group);
		when(cop.getEntity()).thenReturn(body);
		when(group.getTargetPlayerId()).thenReturn(target);
		when(body.getLocation()).thenReturn(spot);
	}

	@Test
	@DisplayName("it is a @ListenerHandler so the bean scan registers it")
	void isAListenerHandler() {
		assertTrue(StandoffListener.class.isAnnotationPresent(ListenerHandler.class));
	}

	@Test
	@DisplayName("0.21.2 T-545: a silent death (nobody saw it) is no casualty until dispatch notices it")
	void silentDeath_noCasualty() {
		listener.onCopDeath(new CopDeathEvent(cop, mock(Player.class), true, spot));

		verify(controller, never()).recordCasualty(any(), any());
	}

	@Test
	@DisplayName("0.21.2 T-545: the death re-fired at discovery counts at the spot he fell, his body long gone")
	void discoveredDeath_bodyGone_recordsCasualtyAtTheDeathSpot() {
		when(cop.getEntity()).thenReturn(null);

		listener.onCopDeath(new CopDeathEvent(cop, mock(Player.class), false, spot));

		verify(controller).recordCasualty(target, spot);
	}

	@Test
	@DisplayName("a cop death records a casualty for its group target at the cop location")
	void copDeath_recordsCasualty() {
		listener.onCopDeath(new CopDeathEvent(cop, mock(Player.class)));

		verify(controller).recordCasualty(target, spot);
	}

	@Test
	@DisplayName("a death with no killer (fall, mob) still counts")
	void copDeathWithoutKiller_recordsCasualty() {
		listener.onCopDeath(new CopDeathEvent(cop, null));

		verify(controller).recordCasualty(target, spot);
	}

	@Test
	@DisplayName("a cop with no group is ignored")
	void copWithoutGroup_ignored() {
		when(cop.getGroup()).thenReturn(null);

		assertDoesNotThrow(() -> listener.onCopDeath(new CopDeathEvent(cop, null)));

		verify(controller, never()).recordCasualty(any(), any());
	}

	@Test
	@DisplayName("a group with no target is ignored")
	void groupWithoutTarget_ignored() {
		when(group.getTargetPlayerId()).thenReturn(null);

		assertDoesNotThrow(() -> listener.onCopDeath(new CopDeathEvent(cop, null)));

		verify(controller, never()).recordCasualty(any(), any());
	}

	@Test
	@DisplayName("a cop whose body is already gone is ignored")
	void copWithoutBody_ignored() {
		when(cop.getEntity()).thenReturn(null);

		assertDoesNotThrow(() -> listener.onCopDeath(new CopDeathEvent(cop, null)));

		verify(controller, never()).recordCasualty(any(), any());
	}
}
