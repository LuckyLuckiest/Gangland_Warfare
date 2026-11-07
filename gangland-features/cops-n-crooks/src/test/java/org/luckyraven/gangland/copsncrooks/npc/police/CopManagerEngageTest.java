package org.luckyraven.gangland.copsncrooks.npc.police;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.BreatherSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DispatchSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * Characterisation of how a freshly spawned cop joins a hunt, on both spawn paths ({@code spawnTick}'s legacy instant
 * refill and the dispatch queue's due units): target set, combat flag from {@code hasCombatAlert(target) ||
 * group.isCombatAlert()}, PURSUING, then added to the group, in that order.
 */
@DisplayName("CopManager - a spawned cop engages the hunt's target")
class CopManagerEngageTest {

	private CopManagerFixture fx;
	private CopManager        manager;
	private Player            player;
	private UUID              playerId;
	private Wanted            wanted;
	private final List<CopNpc> spawned = new ArrayList<>();

	@BeforeEach
	void setUp() {
		fx = new CopManagerFixture();
		when(fx.world.getName()).thenReturn("world");
		manager  = fx.manager;
		player   = fx.player(10, 10);
		playerId = player.getUniqueId();
		wanted   = CopManagerFixture.wanted(2);
		when(fx.spawner.spawnNearPlayer(any(), anyInt(), any(), any())).thenAnswer(inv -> spawnOne());
		when(fx.spawner.spawnUnit(any(), any(), any())).thenAnswer(inv -> spawnOne());
	}

	@AfterEach
	void tearDown() {
		fx.close();
	}

	private CopNpc spawnOne() {
		CopNpc cop = fx.cop(CopState.IDLE, 0, 0);
		spawned.add(cop);
		return cop;
	}

	private CopGroup start(boolean dispatch) {
		if (dispatch) {
			when(fx.provider.getDispatchSettings()).thenReturn(DispatchSettings.DEFAULT);
			when(fx.provider.getBreatherSettings()).thenReturn(BreatherSettings.DEFAULT);
		}
		manager.onWantedStart(player, wanted);
		spawned.clear();
		return manager.groupFor(playerId);
	}

	/** Puts one cop in the group and has another player hit it: the target gets a combat alert, the group does not. */
	private void alertThePlayerOnly(CopGroup group) {
		CopNpc present = fx.cop(CopState.IDLE, 0, 0);
		group.add(present);
		manager.onCopAttackedAlert(present, fx.player(50, 50));
		assertTrue(manager.hasCombatAlert(playerId));
		assertFalse(group.isCombatAlert());
	}

	private void assertEngaged(CopGroup group, boolean forced) {
		assertEquals(1, spawned.size());
		CopNpc cop = spawned.get(0);
		InOrder order = inOrder(cop);
		order.verify(cop).setTargetPlayerId(playerId);
		order.verify(cop).setCombatForced(forced);
		order.verify(cop).transitionTo(CopState.PURSUING);
		order.verify(cop).setGroup(group);
		assertEquals(playerId, cop.getTargetPlayerId());
		assertEquals(forced, cop.isCombatForced());
		assertEquals(CopState.PURSUING, cop.getCurrentState());
		assertTrue(group.getCops().contains(cop));
	}

	private void scenarioNoAlert(boolean dispatch) {
		CopGroup group = start(dispatch);
		group.add(fx.cop(CopState.IDLE, 0, 0));

		manager.spawnTick(playerId, wanted);

		assertEngaged(group, false);
	}

	private void scenarioPlayerAlert(boolean dispatch) {
		CopGroup group = start(dispatch);
		alertThePlayerOnly(group);

		manager.spawnTick(playerId, wanted);

		assertEngaged(group, true);
	}

	private void scenarioGroupAlert(boolean dispatch) {
		CopGroup group = start(dispatch);
		group.add(fx.cop(CopState.IDLE, 0, 0));
		group.escalate(playerId);
		assertFalse(manager.hasCombatAlert(playerId));

		manager.spawnTick(playerId, wanted);

		assertEngaged(group, true);
	}

	@Test
	@DisplayName("instant refill, no alert: targets the player, unforced, pursuing, then joins the group")
	void instantRefill_noAlert() {
		scenarioNoAlert(false);
	}

	@Test
	@DisplayName("instant refill, the player has a combat alert: the new cop is combat-forced")
	void instantRefill_playerAlert() {
		scenarioPlayerAlert(false);
	}

	@Test
	@DisplayName("instant refill, the group is in combat alert: the new cop is combat-forced")
	void instantRefill_groupAlert() {
		scenarioGroupAlert(false);
	}

	@Test
	@DisplayName("dispatched unit, no alert: targets the player, unforced, pursuing, then joins the group")
	void dispatchedUnit_noAlert() {
		scenarioNoAlert(true);
	}

	@Test
	@DisplayName("dispatched unit, the player has a combat alert: the new cop is combat-forced")
	void dispatchedUnit_playerAlert() {
		scenarioPlayerAlert(true);
	}

	@Test
	@DisplayName("dispatched unit, the group is in combat alert: the new cop is combat-forced")
	void dispatchedUnit_groupAlert() {
		scenarioGroupAlert(true);
	}
}
