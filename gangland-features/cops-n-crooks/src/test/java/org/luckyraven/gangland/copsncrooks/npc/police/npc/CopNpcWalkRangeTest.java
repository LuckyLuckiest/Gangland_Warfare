package org.luckyraven.gangland.copsncrooks.npc.police.npc;

import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.npc.TacticsConfig;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The pointman freeze (0.21.1 r6, live 4/4): Citizens stops any walk whose end lies farther than the navigator's
 * pathfinding range ({@code CitizensNavigator.run}: {@code distance > localParams.range()} -> {@code STUCK}) on its
 * next tick, and Keystone 1.15.4 hands it whole route legs to a far goal. A hand-off pointman spawned 239 blocks from
 * its lead under the server's {@code default-range-blocks: 100} logged "route walk failed 2 legs" and stood. The cop
 * now widens its range to cover each goal it walks to.
 */
@DisplayName("CopNpc - the Citizens pathfinding range covers every walk's goal")
class CopNpcWalkRangeTest {

	private static final float SERVER_RANGE = 100f;

	private final World               world    = mock(World.class);
	private       NavigatorParameters defaults;
	private       NavigatorParameters local;
	private       CopNpc              cop;

	@BeforeEach
	void setUp() {
		NPC npc = mock(NPC.class, RETURNS_DEEP_STUBS);
		defaults = new NavigatorParameters().range(SERVER_RANGE);
		local    = new NavigatorParameters().range(SERVER_RANGE);
		when(npc.getNavigator().getDefaultParameters()).thenReturn(defaults);
		when(npc.getNavigator().getLocalParameters()).thenReturn(local);
		LivingEntity body = mock(LivingEntity.class);
		when(body.getWorld()).thenReturn(world);
		when(body.getLocation()).thenReturn(new Location(world, -22, -60, 238)); // the live repro's pointman
		when(npc.getEntity()).thenReturn(body);
		cop = new CopNpc(mock(JavaPlugin.class), npc, tier(), Map.of(), new Location(world, -22, -60, 238),
		                 mock(CopConfigProvider.class));
	}

	@Test
	@DisplayName("a hand-off pointman pursuing toward a lead 239 blocks off gets a range that reaches it")
	void pursuitTowardAFarLead_rangeCoversTheLead() {
		NpcSquad squad = new NpcSquad();
		squad.reportTip(new Location(world, -44, -60, 0)); // the R5 lead
		Player runner = mock(Player.class);
		when(runner.getWorld()).thenReturn(world);

		cop.pursue(runner, squad, 40.0);

		double toLead = Math.sqrt(22 * 22 + 238 * 238);
		assertTrue(defaults.range() > toLead, "default range " + defaults.range() + " must reach the lead " + toLead);
		assertTrue(local.range() > toLead, "and the walk under way");
	}

	@Test
	@DisplayName("a walk home to a station 180 blocks off gets a range that reaches it")
	void walkToAFarStation_rangeCoversIt() {
		cop.navigateTo(new Location(world, -22, -60, 58));

		assertTrue(defaults.range() > 180.0);
	}

	@Test
	@DisplayName("a near goal leaves (or puts back) the server's own range")
	void nearGoal_keepsTheServerRange() {
		cop.navigateTo(new Location(world, -22, -60, 58));
		cop.navigateTo(new Location(world, -22, -60, 228));

		assertEquals(SERVER_RANGE, defaults.range());
	}

	private static CopTierConfig tier() {
		return new CopTierConfig(1, "&9Officer", 40.0, 2.0, 1.2, 2.0, false, false, List.of(), List.of(), null, null,
		                         null, null, NpcDifficulty.EASY, TacticsConfig.DEFAULT, 0.25);
	}
}
