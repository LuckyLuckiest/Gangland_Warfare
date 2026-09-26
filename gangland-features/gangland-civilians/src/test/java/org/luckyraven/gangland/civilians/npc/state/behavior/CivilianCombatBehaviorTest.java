package org.luckyraven.gangland.civilians.npc.state.behavior;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.config.CivilianAIBehaviorConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianDropConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianTypeConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianWearableConfig;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.NpcSquad;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase H11 (spec 4.10): a civilian in COMBAT moves through Keystone's squad pursuit with its type's Alert_Range, and
 * gives up (clears its target, IDLE) once nobody in its squad has seen the target for Search_Seconds - the old
 * {@code attackRange * 4} give-up distance is gone.
 */
@DisplayName("CivilianCombatBehavior - squad pursuit and the search window")
class CivilianCombatBehaviorTest {

	private static final double ALERT_RANGE    = 16.0;
	private static final int    SEARCH_SECONDS = 20;

	private World        world;
	private CivilianNpc  npc;
	private LivingEntity target;
	private UUID         targetId;

	@BeforeEach
	void setUp() {
		world    = mock(World.class);
		targetId = UUID.randomUUID();
		target   = mock(LivingEntity.class);
		when(target.getUniqueId()).thenReturn(targetId);
		when(target.getLocation()).thenReturn(new Location(world, 5, 64, 5));

		CivilianAIBehaviorConfig ai = new CivilianAIBehaviorConfig(false, 0, false, 0, true, 4.0, 12.0, 20,
		                                                           NpcDifficulty.NORMAL, ALERT_RANGE, SEARCH_SECONDS);
		npc = mock(CivilianNpc.class);
		when(npc.getTypeConfig()).thenReturn(new CivilianTypeConfig("gang_member", "Gang Member", EntityType.PLAYER,
		                                                            25.0, true, new CivilianWearableConfig("", "", "", ""),
		                                                            List.of(), List.of(), List.of(),
		                                                            new CivilianDropConfig(List.of(), 0.0), ai,
		                                                            "gang_member"));
		when(npc.getTargetEntity()).thenReturn(target);
	}

	@Test
	@DisplayName("nobody in the squad has seen the target for Search_Seconds: the civilian gives up")
	void searchWindowOver_givesUp() {
		NpcSquad squad = new NpcSquad(); // never sighted: millisSinceSighting() == Long.MAX_VALUE
		inSquad(squad);

		new CivilianCombatBehavior().tick(npc);

		verify(npc).pursue(target, squad, ALERT_RANGE);
		verify(npc).setTargetPlayerId(null);
		verify(npc).setTargetEntity(null);
		verify(npc).transitionTo(CivilianState.IDLE);
	}

	@Test
	@DisplayName("a fresh squad sighting keeps the civilian fighting")
	void freshSighting_keepsFighting() {
		NpcSquad squad = new NpcSquad();
		squad.reportSighting(new Location(world, 5, 64, 5));
		inSquad(squad);

		new CivilianCombatBehavior().tick(npc);

		verify(npc).pursue(target, squad, ALERT_RANGE);
		verify(npc, never()).transitionTo(any());
	}

	@Test
	@DisplayName("in combat without a squad for its target, the civilian hunts alone from where the target is now")
	void noSquadForTarget_huntsAloneFromTargetPosition() {
		new CivilianCombatBehavior().tick(npc);

		ArgumentCaptor<NpcSquad> squad = ArgumentCaptor.forClass(NpcSquad.class);
		verify(npc).joinSquad(squad.capture(), eq(targetId));
		assertEquals(new Location(world, 5, 64, 5), squad.getValue().lastKnownLocation());
		verify(npc).pursue(target, squad.getValue(), ALERT_RANGE);
		verify(npc, never()).transitionTo(any());
	}

	@Test
	@DisplayName("leaving combat leaves the squad")
	void onExit_leavesSquad() {
		new CivilianCombatBehavior().onExit(npc);

		verify(npc).leaveSquad();
	}

	private void inSquad(NpcSquad squad) {
		when(npc.getSquad()).thenReturn(squad);
		when(npc.getSquadTargetId()).thenReturn(targetId);
	}
}
