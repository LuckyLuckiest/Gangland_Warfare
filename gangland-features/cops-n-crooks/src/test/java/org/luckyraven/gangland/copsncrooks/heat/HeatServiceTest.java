package org.luckyraven.gangland.copsncrooks.heat;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.combo.KillCombo;
import org.luckyraven.gangland.copsncrooks.combo.KillComboTracker;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 0.12 F1: the heat ledger weighs crimes (streak bonus, turf-war multiplier for kills only), builds heat on the floor
 * of the current star, asks for the star the new heat reaches, and rate-limits {@code Assault_Cop} per player and
 * cop.
 */
@DisplayName("HeatService")
class HeatServiceTest {

	private KillCombo                   killCombo;
	private NpcMarkManager              markManager;
	private Location                    contestedScene;
	private AtomicLong                  now;
	private AtomicReference<HeatConfig> config;
	private HeatService                 service;

	private Player        player;
	private UUID          playerId;
	private Wanted        wanted;
	private List<Integer> triggered;

	@BeforeEach
	void setUp() {
		killCombo      = mock(KillCombo.class);
		markManager    = mock(NpcMarkManager.class);
		contestedScene = mock(Location.class);
		now            = new AtomicLong(1_000_000L);
		config         = new AtomicReference<>(HeatConfig.defaults());
		service        = new HeatService(killCombo, markManager, location -> location == contestedScene, config::get,
		                                 now::get);

		player   = mock(Player.class);
		playerId = UUID.randomUUID();
		when(player.getUniqueId()).thenReturn(playerId);

		wanted = mock(Wanted.class);
		when(wanted.getLevel()).thenReturn(0);
		when(wanted.getMaxLevel()).thenReturn(5);

		triggered = new ArrayList<>();
		service.setStarTrigger((offender, target) -> triggered.add(target));
	}

	@Test
	@DisplayName("classifyKill maps POLICE to Kill_Cop, CIVILIAN to Kill_Civilian and anything else to Kill_Player")
	void classifyKill_followsEntityMark() {
		Entity cop      = mock(Entity.class);
		Entity civilian = mock(Entity.class);
		Entity other    = mock(Entity.class);
		when(markManager.getMark(cop)).thenReturn("POLICE");
		when(markManager.getMark(civilian)).thenReturn("CIVILIAN");
		when(markManager.getMark(other)).thenReturn(null);

		assertEquals(Crime.KILL_COP, service.classifyKill(cop));
		assertEquals(Crime.KILL_CIVILIAN, service.classifyKill(civilian));
		assertEquals(Crime.KILL_PLAYER, service.classifyKill(other));
		assertTrue(service.isCop(cop));
		assertFalse(service.isCop(civilian));
	}

	@Test
	@DisplayName("weigh applies the streak bonus to every crime and the turf-war multiplier to kills only")
	void weigh_streakAndTurfWar() {
		assertEquals(150, service.weigh(Crime.KILL_COP, false, null));
		assertEquals(225, service.weigh(Crime.KILL_COP, true, null));
		assertEquals(75, service.weigh(Crime.KILL_COP, false, contestedScene));
		assertEquals(113, service.weigh(Crime.KILL_COP, true, contestedScene), "150 * 1.5 * 0.5 rounded");
		assertEquals(100, service.weigh(Crime.ASSAULT_COP, false, contestedScene), "assault is not a kill");
		assertEquals(150, service.weigh(Crime.ASSAULT_COP, true, contestedScene));
	}

	@Test
	@DisplayName("recordCrime counts a running kill combo as a streak")
	void recordCrime_streakFromKillCombo() {
		when(killCombo.getTracker(playerId)).thenReturn(mock(KillComboTracker.class));

		assertEquals(120, service.recordCrime(player, wanted, Crime.KILL_PLAYER, null));
		assertEquals(120, service.getHeat(playerId));
	}

	@Test
	@DisplayName("heat below the first threshold raises no star, crossing it asks for star 1")
	void addHeat_crossingThresholdTriggersStar() {
		service.recordCrime(player, wanted, Crime.KILL_PLAYER, null);
		assertEquals(80, service.getHeat(playerId));
		assertTrue(triggered.isEmpty());

		service.recordCrime(player, wanted, Crime.KILL_PLAYER, null);
		assertEquals(160, service.getHeat(playerId));
		assertEquals(List.of(1), triggered);
	}

	@Test
	@DisplayName("a big crime asks for the highest star the heat reaches, not one step")
	void addHeat_triggersTargetLevel() {
		service.addHeat(player, wanted, 460);

		assertEquals(List.of(3), triggered);
	}

	@Test
	@DisplayName("heat builds on the floor of the current star, so stars gained elsewhere are not re-earned")
	void addHeat_buildsOnStarFloor() {
		when(wanted.getLevel()).thenReturn(2);

		service.recordCrime(player, wanted, Crime.KILL_PLAYER, null);

		assertEquals(330, service.getHeat(playerId), "250 floor + 80");
		assertTrue(triggered.isEmpty(), "330 heat is still two stars");

		service.recordCrime(player, wanted, Crime.KILL_COP, null);
		assertEquals(List.of(3), triggered);
	}

	@Test
	@DisplayName("lowerTo caps heat at the new star's floor, clear forgets it")
	void lowerToAndClear() {
		service.addHeat(player, wanted, 800);

		service.lowerTo(playerId, 2, 5);
		assertEquals(250, service.getHeat(playerId));

		service.lowerTo(playerId, 3, 5);
		assertEquals(250, service.getHeat(playerId), "lowerTo never raises heat");

		service.lowerTo(playerId, 0);
		assertEquals(0, service.getHeat(playerId));

		service.addHeat(player, wanted, 50);
		service.clear(playerId);
		assertEquals(0, service.getHeat(playerId));
	}

	@Test
	@DisplayName("Assault_Cop scores once per player and cop per cooldown window")
	void recordAssault_rateLimitedPerCop() {
		Entity cop      = mock(Entity.class);
		Entity otherCop = mock(Entity.class);
		when(cop.getUniqueId()).thenReturn(UUID.randomUUID());
		when(otherCop.getUniqueId()).thenReturn(UUID.randomUUID());

		assertTrue(service.recordAssault(player, wanted, cop));
		assertEquals(100, service.getHeat(playerId));

		now.addAndGet(5_000L);
		assertFalse(service.recordAssault(player, wanted, cop), "same cop inside the window");
		assertTrue(service.recordAssault(player, wanted, otherCop), "a different cop is its own window");

		now.addAndGet(5_000L);
		assertTrue(service.recordAssault(player, wanted, cop), "window elapsed");
		assertEquals(300, service.getHeat(playerId));

		service.clear(playerId);
		assertTrue(service.recordAssault(player, wanted, cop), "clear forgets cooldowns");
	}

	@Test
	@DisplayName("with Heat.Enable off, assaults are ignored")
	void recordAssault_disabled() {
		config.set(new HeatConfig(false, null, 1.5, 0.5, 10, null));
		Entity cop = mock(Entity.class);
		when(cop.getUniqueId()).thenReturn(UUID.randomUUID());

		assertFalse(service.isEnabled());
		assertFalse(service.recordAssault(player, wanted, cop));
		assertEquals(0, service.getHeat(playerId));
	}

	@Test
	@DisplayName("with no config loaded the service uses the defaults")
	void getConfig_nullSupplierValue_usesDefaults() {
		HeatService unloaded = new HeatService(killCombo, markManager, location -> false, () -> null);

		assertTrue(unloaded.isEnabled());
		assertEquals(HeatConfig.DEFAULT_STAR_THRESHOLDS, unloaded.getConfig().getStarThresholds());
	}
}
