package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.PatrolSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.WarningSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.BrandishWarnings;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolFixture;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolServices;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 0.20.0 lane patrol (P-PAT-1, P-PAT-2, P-PAT-4): the beat. A cop walks random points within Beat_Radius of its anchor
 * with a 3 to 6 s pause, reports a wanted player to their squad, opens a warning for a brandished weapon, and never
 * attacks.
 */
@DisplayName("PatrolBehavior - beat walking, wanted reports, brandish warnings")
class PatrolBehaviorTest {

	private static final Location NO_WORLD = new Location(null, 0, 64, 0);

	private PatrolFixture   fx;
	private PatrolSettings  patrolSettings;
	private WarningSettings warningSettings;
	private CopPerception   perception;
	private BrandishWarnings warnings;
	private CopRadio        radio;
	private Set<UUID>       wanted;
	private Map<UUID, CopGroup> groups;
	private List<Player>    online;
	private boolean         npcFlag;
	private Location        anchor;
	private CopNpc          cop;
	private PatrolBehavior  behavior;

	@BeforeEach
	void setUp() {
		fx              = new PatrolFixture();
		patrolSettings  = PatrolSettings.DEFAULT;
		warningSettings = WarningSettings.DEFAULT;
		perception      = new CopPerception();
		warnings        = mock(BrandishWarnings.class);
		radio           = mock(CopRadio.class);
		wanted          = new HashSet<>();
		groups          = new HashMap<>();
		online          = new ArrayList<>();
		anchor          = fx.at(0, 0);
		when(warnings.canWarn(any())).thenReturn(true);
		when(warnings.holding(any())).thenAnswer(inv -> fx.armed.contains(((Player) inv.getArgument(0)).getUniqueId()));

		PatrolServices services = new PatrolServices(() -> patrolSettings, () -> warningSettings, perception,
		                                             player -> wanted.contains(player.getUniqueId()),
		                                             groups::get, warnings, radio, () -> online,
		                                             entity -> npcFlag, () -> fx.clock[0], new Random(7));
		behavior = new PatrolBehavior(services);
		cop      = fx.cop(CopState.PATROL, 0, 0, anchor);
	}

	@AfterEach
	void tearDown() {
		fx.close();
	}

	private Player playerAt(double x, double z) {
		Player player = fx.player(x, z);
		online.add(player);
		return player;
	}

	private CopGroup groupFor(Player player, NpcSquad squad) {
		CopGroup group = mock(CopGroup.class);
		when(group.getSquad()).thenReturn(squad);
		groups.put(player.getUniqueId(), group);
		return group;
	}

	/** Ticks {@code count} times, one second apart, collecting every point the cop was sent to. */
	private List<Location> walk(int count) {
		for (int i = 0; i < count; i++) {
			behavior.tick(cop);
			fx.clock[0] += 1_000;
		}
		org.mockito.ArgumentCaptor<Location> captor = org.mockito.ArgumentCaptor.forClass(Location.class);
		verify(cop, atLeast(0)).navigateTo(captor.capture());
		return captor.getAllValues();
	}

	@Test
	@DisplayName("chosen points are always within Beat_Radius (24) of the anchor, and the cop keeps choosing new ones")
	void walksWithinBeatRadius() {
		List<Location> points = walk(300);

		assertTrue(points.size() >= 5, "the cop should keep walking, got " + points.size());
		for (Location point : points)
			assertTrue(Math.hypot(point.getX() - anchor.getX(), point.getZ() - anchor.getZ()) <= 24.0 + 1e-9,
			           "point out of the beat: " + point);
	}

	@Test
	@DisplayName("the first tick sends the cop somewhere; after it arrives it pauses 3 to 6 s before the next point")
	void pausesBetweenPoints() {
		behavior.tick(cop);
		verify(cop, times(1)).navigateTo(any());

		behavior.tick(cop);
		fx.clock[0] += 2_999;
		behavior.tick(cop);
		verify(cop, times(1)).navigateTo(any());

		fx.clock[0] += 3_002;
		behavior.tick(cop);
		verify(cop, times(2)).navigateTo(any());
	}

	@Test
	@DisplayName("a cop still walking (navigator busy) is not given a new point")
	void stillNavigating_noNewPoint() {
		behavior.tick(cop);
		when(cop.getNpc().getNavigator().isNavigating()).thenReturn(true);

		fx.clock[0] += 60_000;
		behavior.tick(cop);
		behavior.tick(cop);

		verify(cop, times(1)).navigateTo(any());
	}

	@Test
	@DisplayName("points in unloaded chunks are never chosen")
	void unloadedChunk_noWalk() {
		when(fx.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);

		walk(20);

		verify(cop, never()).navigateTo(any());
	}

	@Test
	@DisplayName("an anchor without a world walks nowhere and does not throw")
	void nullWorld_noWalk() {
		when(cop.getSpawnLocation()).thenReturn(NO_WORLD);

		walk(20);

		verify(cop, never()).navigateTo(any());
	}

	@Test
	@DisplayName("a cop outside its beat (after a fight) walks back to within Beat_Radius first")
	void farFromAnchor_walksBack() {
		CopNpc far = fx.cop(CopState.PATROL, 40, 0, anchor);

		behavior.tick(far);

		org.mockito.ArgumentCaptor<Location> captor = org.mockito.ArgumentCaptor.forClass(Location.class);
		verify(far).navigateTo(captor.capture());
		assertTrue(Math.hypot(captor.getValue().getX(), captor.getValue().getZ()) <= 24.0 + 1e-9);
	}

	@Test
	@DisplayName("a wanted player in view is reported to their squad and radioed once while the sighting is fresh")
	void wantedPlayerReportedToSquad() {
		Player player = playerAt(5, 0);
		wanted.add(player.getUniqueId());
		NpcSquad squad = new NpcSquad();
		CopGroup group = groupFor(player, squad);

		behavior.tick(cop);
		behavior.tick(cop);

		assertEquals(fx.at(5, 0), squad.lastKnownLocation());
		verify(radio, times(1)).sayFromLeader(group, "Patrol_Sighting");
	}

	@Test
	@DisplayName("a wanted player with no group (no squad) is skipped without an error")
	void wantedWithoutGroup_nothing() {
		Player player = playerAt(5, 0);
		wanted.add(player.getUniqueId());

		behavior.tick(cop);

		verify(radio, never()).sayFromLeader(any(), anyString());
		verify(cop, never()).transitionTo(any());
	}

	@Test
	@DisplayName("Cops.Patrol.Report_Wanted false: a wanted player is not reported")
	void reportWantedOff_noReport() {
		patrolSettings = new PatrolSettings(true, 2, 64, 96, 30, 24, 3, 6, 1, false);
		Player player = playerAt(5, 0);
		wanted.add(player.getUniqueId());
		NpcSquad squad = new NpcSquad();
		groupFor(player, squad);

		behavior.tick(cop);

		assertNull(squad.lastKnownLocation());
	}

	@Test
	@DisplayName("a wanted player the cop does not acquire (darkness, disguise) is not reported")
	void wantedNotAcquired_noReport() {
		Player player = playerAt(5, 0);
		wanted.add(player.getUniqueId());
		NpcSquad squad = new NpcSquad();
		groupFor(player, squad);
		perception.addLockOnFilter((c, p) -> false);

		behavior.tick(cop);

		assertNull(squad.lastKnownLocation());
	}

	@Test
	@DisplayName("a wanted player beyond Warning.Range is not reported")
	void wantedBeyondRange_noReport() {
		Player player = playerAt(30, 0);
		wanted.add(player.getUniqueId());
		NpcSquad squad = new NpcSquad();
		groupFor(player, squad);

		behavior.tick(cop);

		assertNull(squad.lastKnownLocation());
	}

	@Test
	@DisplayName("a wanted and armed player is reported, never warned")
	void wantedAndArmed_reportedNotWarned() {
		Player player = playerAt(5, 0);
		wanted.add(player.getUniqueId());
		fx.armed.add(player.getUniqueId());
		NpcSquad squad = new NpcSquad();
		groupFor(player, squad);

		behavior.tick(cop);

		assertEquals(fx.at(5, 0), squad.lastKnownLocation());
		verify(warnings, never()).begin(any());
	}

	@Test
	@DisplayName("a non-wanted player holding a weapon in view opens a warning and the cop enters WARNING facing them")
	void armedPlayerStartsWarning() {
		Player player = playerAt(5, 0);
		fx.armed.add(player.getUniqueId());
		when(warnings.begin(player)).thenReturn(true);

		behavior.tick(cop);

		verify(warnings).begin(player);
		verify(cop).setTargetPlayerId(player.getUniqueId());
		verify(cop).transitionTo(CopState.WARNING);
		verify(cop, never()).navigateTo(any());
	}

	@Test
	@DisplayName("an armed player on warning cooldown is left alone")
	void armedOnCooldown_noWarning() {
		Player player = playerAt(5, 0);
		fx.armed.add(player.getUniqueId());
		when(warnings.canWarn(player)).thenReturn(false);

		behavior.tick(cop);

		verify(warnings, never()).begin(any());
		verify(cop, never()).transitionTo(CopState.WARNING);
	}

	@Test
	@DisplayName("an unarmed player is never warned")
	void unarmed_noWarning() {
		playerAt(5, 0);

		behavior.tick(cop);

		verify(warnings, never()).begin(any());
	}

	@Test
	@DisplayName("an armed player beyond Warning.Range (12) is not warned")
	void armedOutOfRange_noWarning() {
		Player player = playerAt(13, 0);
		fx.armed.add(player.getUniqueId());

		behavior.tick(cop);

		verify(warnings, never()).begin(any());
	}

	@Test
	@DisplayName("an armed player the cop does not acquire is not warned")
	void armedNotAcquired_noWarning() {
		Player player = playerAt(5, 0);
		fx.armed.add(player.getUniqueId());
		perception.addLockOnFilter((c, p) -> false);

		behavior.tick(cop);

		verify(warnings, never()).begin(any());
	}

	@Test
	@DisplayName("Cops.Warning.Enable false: no warnings")
	void warningDisabled_noWarning() {
		warningSettings = new WarningSettings(false, 12.0, 5, 30);
		Player player = playerAt(5, 0);
		fx.armed.add(player.getUniqueId());

		behavior.tick(cop);

		verify(warnings, never()).begin(any());
	}

	@Test
	@DisplayName("Citizens NPC players and dead or offline players are ignored")
	void npcDeadOffline_ignored() {
		Player npcPlayer = playerAt(5, 0);
		Player dead      = playerAt(4, 0);
		Player offline   = playerAt(3, 0);
		for (Player p : List.of(npcPlayer, dead, offline))
			fx.armed.add(p.getUniqueId());
		npcFlag = false;
		when(dead.isDead()).thenReturn(true);
		when(offline.isOnline()).thenReturn(false);
		behavior = new PatrolBehavior(new PatrolServices(() -> patrolSettings, () -> warningSettings, perception,
		                                                 p -> false, groups::get, warnings, radio, () -> online,
		                                                 entity -> entity == npcPlayer, () -> fx.clock[0],
		                                                 new Random(7)));

		behavior.tick(cop);

		verify(warnings, never()).begin(any());
	}

	@Test
	@DisplayName("wanted and armed players in range for 200 ticks: the beat cop never attacks")
	void neverAttacks() {
		Player wantedPlayer = playerAt(5, 0);
		wanted.add(wantedPlayer.getUniqueId());
		groupFor(wantedPlayer, new NpcSquad());
		Player armedPlayer = playerAt(6, 1);
		fx.armed.add(armedPlayer.getUniqueId());

		for (int i = 0; i < 200; i++) {
			behavior.tick(cop);
			fx.clock[0] += 500;
		}

		verify(cop, never()).attack(any(Player.class));
		verify(cop, never()).attackEntity(any(LivingEntity.class));
		verify(cop, never()).pursue(any(), any(), anyDouble());
	}

	@Test
	@DisplayName("onEnter and onExit do not attack or throw")
	void lifecycle_isSafe() {
		behavior.onEnter(cop);
		behavior.onExit(cop);

		verify(cop, never()).attack(any(Player.class));
		verify(cop, never()).attackEntity(any(LivingEntity.class));
	}
}
