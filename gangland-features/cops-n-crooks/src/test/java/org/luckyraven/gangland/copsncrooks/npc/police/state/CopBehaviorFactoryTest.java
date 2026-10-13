package org.luckyraven.gangland.copsncrooks.npc.police.state;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.npc.RetreatSettings;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link CopBehaviorFactory}: every {@link CopState} has a behaviour, and the shared {@link CopPerception} reaches it. */
@DisplayName("CopBehaviorFactory - behaviours for every state, perception wired")
class CopBehaviorFactoryTest {

	private static final double ALERT_RANGE = 40.0;

	private BukkitStatics     bukkit;
	private CopConfigProvider provider;

	@BeforeEach
	void setUp() {
		bukkit   = BukkitStatics.install();
		provider = mock(CopConfigProvider.class);
		when(provider.getAiTickRate()).thenReturn(10);
		when(provider.getCuffCooldownTicks()).thenReturn(40);
		when(provider.getAlertRange()).thenReturn(ALERT_RANGE);
		when(provider.getRetreatSettings()).thenReturn(RetreatSettings.DEFAULT);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private CopBehaviorFactory factory() {
		return new CopBehaviorFactory(provider, () -> mock(CopSpawnManager.class), mock(DetainmentService.class),
		                              new CuffLockRegistry(), id -> false);
	}

	@Test
	@DisplayName("createBehaviors has a non-null behaviour for every CopState (the EnumMap has no hole)")
	void everyStateHasBehaviour() {
		Map<CopState, CopBehavior> behaviors = factory().createBehaviors();

		for (CopState state : CopState.values())
			assertNotNull(behaviors.get(state), "no behaviour for " + state);
		assertEquals(CopState.values().length, behaviors.size());
	}

	@Test
	@DisplayName("the three 0.20 states are appended after POSTED, in order, so stored ordinals do not shift")
	void newStatesAreAppendedInOrder() {
		List<CopState> all = List.of(CopState.values());

		assertEquals(CopState.POSTED, all.get(all.size() - 4));
		assertEquals(List.of(CopState.PATROL, CopState.WARNING, CopState.OVERWATCH),
		             all.subList(all.size() - 3, all.size()));
	}

	@Test
	@DisplayName("the perception handed to the factory is the one the idle behaviour acquires through")
	void factoryPassesPerceptionToIdle() {
		UUID   playerId = UUID.randomUUID();
		Player player   = mock(Player.class);
		bukkit.statics().when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
		CopNpc cop = mock(CopNpc.class);
		when(cop.getTargetPlayerId()).thenReturn(playerId);
		when(cop.squadFor(player)).thenReturn(new NpcSquad());
		when(cop.canSee(player, ALERT_RANGE)).thenReturn(true);
		CopPerception perception = mock(CopPerception.class);
		CopBehaviorFactory factory = new CopBehaviorFactory(provider, () -> mock(CopSpawnManager.class),
		                                                    mock(DetainmentService.class), new CuffLockRegistry(),
		                                                    id -> false, perception);

		factory.createBehaviors().get(CopState.IDLE).tick(cop);

		verify(perception).acquires(cop, player, ALERT_RANGE);
		verify(cop, never()).transitionTo(any());
	}
}
