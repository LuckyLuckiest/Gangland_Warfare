package org.luckyraven.gangland.turf.npc.defender;

import com.cryptomorin.xseries.XAttribute;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.events.CivilianDeathEvent;
import org.luckyraven.gangland.civilians.listener.civilian.CivilianDeathListener;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.FactionSquads;
import org.luckyraven.gangland.civilians.npc.config.CivilianAIBehaviorConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianDropConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianTypeConfig;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyDepositService;
import org.luckyraven.gangland.turf.listener.powerups.TurfDefenderDeathListener;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.events.TurfDefenderDownEvent;
import org.luckyraven.gangland.npc.NpcRole;
import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.luckyraven.keystone.npc.NpcPost;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;
import org.bukkit.event.Event;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@link TurfDefenderDeployer#findOwningTurfId(Entity)} (moved into gangland-turf in group I; zero coverage
 * before this pin — E3 §6, T-N3): resolves the turf id whose defender group owns the given entity, and returns
 * {@code -1} both for an untracked entity and for {@code null}. {@code byTurfId} and the private {@code Group}/
 * {@code TrackedDefender} types have no public seam, so the fixture is assembled through reflection — same house
 * pattern as {@code TurfPowerupManagerTest} (this module) and {@code CaptureServiceHelpersTest}.
 */
@DisplayName("TurfDefenderDeployer")
class TurfDefenderDeployerTest {

	@Test
	@DisplayName("findOwningTurfId_isAnIndexLookup: the lookup goes by entity UUID, so a second entity object with the same UUID resolves")
	void findOwningTurfId_isAnIndexLookup() {
		TurfDefenderDeployer deployer = newDeployer();
		UUID id = UUID.randomUUID();
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getUniqueId()).thenReturn(id);
		CivilianNpc npc = liveNpc();
		when(npc.getEntity()).thenReturn(entity);
		putDefender(deployer, 4, npc);

		LivingEntity sameUuid = mock(LivingEntity.class);
		when(sameUuid.getUniqueId()).thenReturn(id);

		assertEquals(4, deployer.findOwningTurfId(sameUuid));
	}

	@Test
	@DisplayName("markKilled_byUuid: a guard tracked by entity UUID counts as down once it is gone, an untracked NPC does not")
	void markKilled_byUuid() {
		TurfDefenderDeployer deployer = newDeployer();
		CivilianNpc dying = liveNpc();
		CivilianNpc stranger = liveNpc();
		putDefender(deployer, 1, dying);
		putDefender(deployer, 1, liveNpc());
		when(dying.isValid()).thenReturn(false);
		deployer.markKilled(dying);
		deployer.markKilled(stranger);
		when(stranger.isValid()).thenReturn(false);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			deployer.tick();

			ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
			verify(bukkit.pluginManager(), org.mockito.Mockito.times(1)).callEvent(captor.capture());
			assertEquals(1, ((TurfDefenderDownEvent) captor.getValue()).remaining());
		}
	}

	@Test
	@DisplayName("noCitizens_consumesNothing: TF-46, a garrison start without Citizens spawns and spends nothing")
	void noCitizens_consumesNothing() {
		CivilianSpawnManager spawnManager = mock(CivilianSpawnManager.class);
		TurfDefenderDeployer deployer = new TurfDefenderDeployer(mock(JavaPlugin.class), mock(CivilianService.class),
				spawnManager);
		deployer.setCitizensAvailable(() -> false);
		AtomicInteger consumed = new AtomicInteger();
		TurfGarrison garrison = new TurfGarrison(1, new CuboidRegion("world", 0, 0, 20, 20),
				new TurfDefenderConfig("turf_defender", 32.0), "VIP", () -> 5, n -> {
			consumed.addAndGet(n);
			return n;
		}, n -> {
		});

		deployer.startGarrison(garrison, Set::of);
		deployer.tick();

		assertEquals(0, consumed.get());
		assertEquals(0, deployer.aliveCount(1));
		verifyNoInteractions(spawnManager);
	}

	@Test
	@DisplayName("a spawn spends nothing: only a contest kill debits; a failed spawn stops the batch and starts the cooldown")
	void wave_spendsNothing_failedSpawnStopsBatch() {
		try (Rig rig = new Rig(10)) {
			CivilianNpc first  = guardAt(new Location(rig.world, 2.5, 65, 2.5));
			CivilianNpc second = guardAt(new Location(rig.world, 2.5, 65, 2.5));
			when(rig.spawnManager.spawnCivilian(any(), eq("turf_defender"))).thenReturn(first, second, null);

			rig.contest();
			assertEquals(10, rig.stock.get(), "arming the garrison spends nothing");
			rig.ticks(1);

			// the wave asked for 3: two spawned, the third came back null
			assertEquals(10, rig.stock.get());
			assertEquals(2, rig.notices.get());
			assertEquals(2, rig.alive());
			assertTrue(rig.garrison().lastLossAt >= 0, "a failed spawn waits out the cooldown");
		}
	}

	@Test
	@DisplayName("a challenger far outside the leash is ignored: no ray is traced for it")
	void farChallenger_causesNoRayTrace() {
		try (Rig rig = new Rig(5)) {
			UUID id = UUID.randomUUID();
			Player far = mock(Player.class);
			when(far.getUniqueId()).thenReturn(id);
			when(far.getEyeLocation()).thenReturn(new Location(null, 2000, 70, 2000));
			when(far.getLocation()).thenReturn(new Location(rig.world, 2000, 70, 2000));
			when(rig.world.getPlayers()).thenReturn(List.of(far));

			rig.contest(id);
			rig.ticks(1);

			assertEquals(3, rig.spawned.size());
			verify(rig.world, never()).rayTraceBlocks(any(Location.class), any(org.bukkit.util.Vector.class), anyDouble(),
					any(org.bukkit.FluidCollisionMode.class), anyBoolean());
		}
	}

	@Test
	@DisplayName("F-GARRISON r1: a restart during the stand-down window re-arms the same guards: no removal, no extra body")
	void standDown_rivalStillOnTurf_restartRearms() {
		try (Rig rig = new Rig(1)) {
			rig.contest();
			rig.ticks(1);
			CivilianNpc guard = rig.spawned.get(0);
			TurfGarrison garrison = rig.garrison();

			rig.deployer.standDown(1, true);
			assertTrue(garrison.standingDown, "standDown runs even with a rival on the turf");

			rig.contest();
			assertFalse(garrison.standingDown, "the restart re-arms the garrison");
			rig.ticks(1);

			assertEquals(1, rig.alive(), "the restarted contest keeps the standing guard");
			assertEquals(1, rig.spawned.size());
			verify(guard, never()).markForRemoval();
		}
	}

	@Test
	@DisplayName("F-GARRISON r1: with no restart the window ends and the guard stays, since stock 1 still pays for it")
	void standDown_noRestart_guardStaysAfterWindow() {
		try (Rig rig = new Rig(1)) {
			rig.contest();
			rig.ticks(1);
			CivilianNpc guard = rig.spawned.get(0);

			rig.deployer.standDown(1, true);
			rig.garrison().standDownAt = 0L;
			rig.ticks(2);

			assertFalse(rig.garrison().standingDown);
			assertEquals(1, rig.alive());
			assertEquals(1, rig.stock.get());
			verify(guard, never()).markForRemoval();
		}
	}

	@Test
	@DisplayName("T-751: standDown stands the guards down at once")
	void standDown_rivalGone_standsDown() {
		try (Rig rig = new Rig(1)) {
			rig.contest();
			rig.ticks(1);

			rig.deployer.standDown(1, true);

			assertTrue(rig.garrison().standingDown);
		}
	}

	@Test
	@DisplayName("resolves the turf id owning a tracked, valid defender's entity")
	void findOwningTurfId_matchesTrackedDefender() {
		TurfDefenderDeployer deployer = newDeployer();

		LivingEntity entity = mock(LivingEntity.class);
		CivilianNpc  npc    = mock(CivilianNpc.class);
		when(npc.isValid()).thenReturn(true);
		when(npc.getEntity()).thenReturn(entity);
		when(entity.getUniqueId()).thenReturn(UUID.randomUUID());

		putDefender(deployer, 7, npc);

		assertEquals(7, deployer.findOwningTurfId(entity));
	}

	@Test
	@DisplayName("returns -1 for an entity that belongs to no tracked defender, and for null")
	void findOwningTurfId_untrackedEntityOrNull_returnsMinusOne() {
		TurfDefenderDeployer deployer = newDeployer();

		LivingEntity tracked   = mock(LivingEntity.class);
		LivingEntity untracked = mock(LivingEntity.class);
		CivilianNpc  npc       = mock(CivilianNpc.class);
		when(npc.isValid()).thenReturn(true);
		when(npc.getEntity()).thenReturn(tracked);
		when(tracked.getUniqueId()).thenReturn(UUID.randomUUID());
		when(untracked.getUniqueId()).thenReturn(UUID.randomUUID());

		putDefender(deployer, 3, npc);

		assertEquals(-1, deployer.findOwningTurfId(untracked));
		assertEquals(-1, deployer.findOwningTurfId(null));
	}

	@Test
	@DisplayName("aliveCount counts live guards per turf and drops one when a guard turns invalid")
	void aliveCount_isPerTurf() {
		TurfDefenderDeployer deployer = newDeployer();
		CivilianNpc a = liveNpc();
		CivilianNpc b = liveNpc();
		CivilianNpc c = liveNpc();
		putDefender(deployer, 1, a);
		putDefender(deployer, 1, b);
		putDefender(deployer, 2, c);

		assertEquals(2, deployer.aliveCount(1));
		assertEquals(1, deployer.aliveCount(2));
		assertEquals(0, deployer.aliveCount(3));

		when(a.isValid()).thenReturn(false);

		assertEquals(1, deployer.aliveCount(1));
	}

	@Test
	@DisplayName("a guard that dies fires one TurfDefenderDownEvent per tick with the guards left")
	void deadDefender_firesDefenderDownOncePerTick() {
		TurfDefenderDeployer deployer = newDeployer();
		CivilianNpc alive = liveNpc();
		CivilianNpc dying = liveNpc();
		putDefender(deployer, 1, alive);
		putDefender(deployer, 1, dying);
		when(dying.isValid()).thenReturn(false);
		deployer.markKilled(dying);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			deployer.tick();

			ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
			verify(bukkit.pluginManager(), org.mockito.Mockito.times(1)).callEvent(captor.capture());
			assertInstanceOf(TurfDefenderDownEvent.class, captor.getValue());
			TurfDefenderDownEvent down = (TurfDefenderDownEvent) captor.getValue();
			assertEquals(1, down.turfId());
			assertEquals(1, down.remaining());
		}
	}

	@Test
	@DisplayName("T-251: a guard that vanished without dying (despawned or unloaded) fires no TurfDefenderDownEvent")
	void despawnedDefender_firesNoDefenderDown() {
		TurfDefenderDeployer deployer = newDeployer();
		CivilianNpc alive     = liveNpc();
		CivilianNpc despawned = liveNpc();
		putDefender(deployer, 1, alive);
		putDefender(deployer, 1, despawned);
		when(despawned.isValid()).thenReturn(false);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			deployer.tick();

			verify(bukkit.pluginManager(), org.mockito.Mockito.never()).callEvent(org.mockito.ArgumentMatchers.any(Event.class));
		}
		assertEquals(1, deployer.aliveCount(1));
	}

	@Test
	@DisplayName("T-285: two guard deaths run CivilianDeathListener -> TurfDefenderDeathListener -> tick and fire one TurfDefenderDownEvent(remaining 2, deployed 4)")
	void twoDeaths_throughTheRealListeners_fireOneDefenderDown() throws Exception {
		TurfDefenderDeployer deployer = newDeployer();
		Map<UUID, CivilianNpc> registry = new HashMap<>();
		List<LivingEntity> entities = new ArrayList<>();
		List<CivilianNpc> guards = new ArrayList<>();
		CivilianTypeConfig type = mock(CivilianTypeConfig.class);
		when(type.drops()).thenReturn(new CivilianDropConfig(List.of(), 0.0));
		for (int i = 0; i < 4; i++) {
			LivingEntity entity = mock(LivingEntity.class);
			when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
			CivilianNpc npc = liveNpc();
			when(npc.getEntity()).thenReturn(entity);
			when(npc.getTypeConfig()).thenReturn(type);
			registry.put(entity.getUniqueId(), npc);
			entities.add(entity);
			guards.add(npc);
			putDefender(deployer, 1, npc);
		}
		// putDefender -> track() already counted the four deployments
		CivilianService service = mock(CivilianService.class);
		when(service.getNpc(org.mockito.ArgumentMatchers.any(UUID.class)))
				.thenAnswer(call -> registry.get(call.<UUID>getArgument(0)));
		CivilianDeathListener civilianListener = new CivilianDeathListener(service, null);
		TurfDefenderDeathListener turfListener = new TurfDefenderDeathListener(deployer, new GangMembership(),
				mock(MoneyAddon.class), mock(MoneyDepositService.class));

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			org.mockito.Mockito.doAnswer(call -> {
				if (call.getArgument(0) instanceof CivilianDeathEvent death) {
					turfListener.onCivilianDeath(death);
				}
				return null;
			}).when(bukkit.pluginManager()).callEvent(org.mockito.ArgumentMatchers.any(CivilianDeathEvent.class));

			for (int i = 0; i < 2; i++) {
				EntityDeathEvent death = mock(EntityDeathEvent.class);
				when(death.getEntity()).thenReturn(entities.get(i));
				when(death.getDrops()).thenReturn(new ArrayList<>());
				civilianListener.onCivilianDeath(death);
				when(guards.get(i).isValid()).thenReturn(false);
			}
			deployer.tick();

			ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
			verify(bukkit.pluginManager(), org.mockito.Mockito.atLeastOnce()).callEvent(captor.capture());
			List<TurfDefenderDownEvent> downs = captor.getAllValues().stream()
					.filter(TurfDefenderDownEvent.class::isInstance).map(TurfDefenderDownEvent.class::cast).toList();
			assertEquals(1, downs.size());
			assertEquals(2, downs.get(0).remaining());
			assertEquals(4, downs.get(0).deployed());
		}
	}

	// ---- W7-F: Hold / Defend order ----

	private static final GarrisonPosts.Post POST = new GarrisonPosts.Post(2.5, 65.0, 2.5);

	private static org.bukkit.entity.Player playerAt(World world, UUID id, double x, double z) {
		org.bukkit.entity.Player player = mock(org.bukkit.entity.Player.class);
		when(player.getUniqueId()).thenReturn(id);
		when(player.getLocation()).thenReturn(new org.bukkit.Location(world, x, 65.0, z));
		return player;
	}

	/** Runs one tick with a guard at (guardX, guardZ) and the given challengers; returns the guard. */
	private static CivilianNpc tickHoldFixture(java.util.function.IntPredicate binding, double guardX, double guardZ,
	                                           World world, Map<UUID, double[]> challengers) {
		TurfDefenderDeployer deployer = newDeployer();
		deployer.setCitizensAvailable(() -> true);
		if (binding != null) {
			deployer.bindHolding(binding);
		}
		CivilianNpc guard = liveNpc();
		LivingEntity body = mock(LivingEntity.class);
		when(body.getUniqueId()).thenReturn(UUID.randomUUID());
		when(body.getWorld()).thenReturn(world);
		when(body.getLocation()).thenReturn(new org.bukkit.Location(world, guardX, 65.0, guardZ));
		when(guard.getEntity()).thenReturn(body);
		List<org.bukkit.entity.Player> players = new ArrayList<>();
		for (Map.Entry<UUID, double[]> c : challengers.entrySet()) {
			players.add(playerAt(world, c.getKey(), c.getValue()[0], c.getValue()[1]));
		}
		when(world.getPlayers()).thenReturn(players);
		TurfGarrison garrison = new TurfGarrison(1, new CuboidRegion("world", 0, 0, 20, 20),
				new TurfDefenderConfig("turf_defender", 32.0, 3, 6, 20, 0, 16), "VIP", () -> 0, n -> n, n -> {
		});
		garrison.posts = List.of(POST);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(() -> Bukkit.getWorld("world")).thenReturn(world);
			deployer.startGarrison(garrison, () -> challengers.keySet());
			deployer.track(1, guard);
			deployer.tick();
		}
		return guard;
	}

	@Test
	@DisplayName("hold_guardFarFromPost_walksBackAndDropsTarget: Hold sends a guard 20 blocks from its post home and does not target")
	void hold_guardFarFromPost_walksBackAndDropsTarget() {
		World world = mock(World.class);
		UUID  id    = UUID.randomUUID();

		CivilianNpc guard = tickHoldFixture(turfId -> true, 18.5, 18.5, world, Map.of(id, new double[]{19.5, 18.5}));

		verify(guard).setTargetPlayerId(null);
		verify(guard).navigateTo(new org.bukkit.Location(world, POST.x(), POST.y(), POST.z()));
		verify(guard, org.mockito.Mockito.never()).setTargetPlayerId(id);
	}

	@Test
	@DisplayName("hold_targetsOnlyNearPost: at its post a guard takes the attacker 4 blocks away, not the one 12 blocks away")
	void hold_targetsOnlyNearPost() {
		UUID near = UUID.randomUUID();
		UUID far  = UUID.randomUUID();

		CivilianNpc both = tickHoldFixture(turfId -> true, POST.x(), POST.z(), mock(World.class),
				Map.of(near, new double[]{6.5, 2.5}, far, new double[]{14.5, 2.5}));
		verify(both).setTargetPlayerId(near);

		CivilianNpc onlyFar = tickHoldFixture(turfId -> true, POST.x(), POST.z(), mock(World.class),
				Map.of(far, new double[]{14.5, 2.5}));
		verify(onlyFar, org.mockito.Mockito.never()).setTargetPlayerId(org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("defend_unchanged: the same far-attacker fixture without Hold still targets it")
	void defend_unchanged() {
		UUID far = UUID.randomUUID();

		CivilianNpc unbound = tickHoldFixture(null, POST.x(), POST.z(), mock(World.class),
				Map.of(far, new double[]{14.5, 2.5}));
		verify(unbound).setTargetPlayerId(far);

		CivilianNpc defending = tickHoldFixture(turfId -> false, POST.x(), POST.z(), mock(World.class),
				Map.of(far, new double[]{14.5, 2.5}));
		verify(defending).setTargetPlayerId(far);
	}

	// ---- Standing guards: reconcile and spawn ----

	@Test
	@DisplayName("start: the first tick waits 60 ticks for worlds and Citizens, then every 5")
	void start_firstTickAfterSixty() {
		try (Rig rig = new Rig(0)) {
			rig.deployer.start();

			verify(rig.bukkit.scheduler()).runTaskTimer(any(org.bukkit.plugin.Plugin.class), any(Runnable.class), eq(60L), eq(5L));
		}
	}

	@Test
	@DisplayName("stock 10 with no contest: two ticks stand 5 guards, nothing contests, the count stays 10")
	void stockedTurf_spawnsToStandingCap_noContest() {
		try (Rig rig = new Rig(10)) {
			rig.ticks(2);

			assertEquals(5, rig.spawned.size());
			assertEquals(5, rig.alive());
			assertFalse(rig.contested());
			assertEquals(10, rig.stock.get());
		}
	}

	@Test
	@DisplayName("TF-46: without Citizens a stocked turf builds no group: no spawn, no debit")
	void noCitizens_buildsNoGroup() {
		try (Rig rig = new Rig(10)) {
			rig.deployer.setCitizensAvailable(() -> false);

			rig.ticks(3);

			assertTrue(rig.groups().isEmpty());
			assertEquals(0, rig.spawned.size());
			assertEquals(10, rig.stock.get());
		}
	}

	@Test
	@DisplayName("T-298 standing: the five guards stand at the five distinct posts")
	void peaceSpawn_oneGuardPerPost() {
		try (Rig rig = new Rig(10)) {
			rig.ticks(2);

			assertEquals(5, rig.spawned.size());
			Set<String> spots = new HashSet<>();
			for (Location at : rig.spawnedAt) {
				spots.add(at.getX() + "," + at.getZ());
			}
			Set<String> posts = new HashSet<>();
			for (GarrisonPosts.Post post : GarrisonPosts.posts(rig.region, (x, z) -> 64)) {
				posts.add(post.x() + "," + post.z());
			}
			assertEquals(posts, spots);
		}
	}

	@Test
	@DisplayName("Standing_Cap 0 is the reserve-only switch: no guard stands in peace")
	void standingCap_zero_spawnsNothingInPeace() {
		try (Rig rig = new Rig(10)) {
			rig.config = config(0, 6, 3, 20, 8, GarrisonComposition.NONE);

			rig.ticks(3);

			assertEquals(0, rig.spawned.size());
			rig.contest();
			rig.ticks(1);
			assertEquals(3, rig.spawned.size(), "the same fixture does spawn once contested");
		}
	}

	@Test
	@DisplayName("Standing_Cap 8 under Max_Alive 6: 6 stand")
	void standingCap_cappedByMaxAlive() {
		try (Rig rig = new Rig(10)) {
			rig.config = config(8, 6, 3, 20, 8, GarrisonComposition.NONE);

			rig.ticks(5);

			assertEquals(6, rig.spawned.size());
			assertEquals(6, rig.alive());
		}
	}

	@Test
	@DisplayName("a stocked id whose turf is gone or unclaimed builds no group and spawns nothing; a good id beside it still spawns")
	void reconcile_skipsGoneAndUnclaimedTurfs() {
		try (Rig rig = new Rig(10)) {
			when(rig.roster.stockedTurfIds()).thenReturn(List.of(1, 2));
			when(rig.roster.garrisonOf(1)).thenReturn(null);
			when(rig.roster.garrisonOf(2)).thenAnswer(call -> rig.fresh(2));
			when(rig.roster.configOf(2)).thenAnswer(call -> rig.config);
			when(rig.roster.guardHealth(2)).thenReturn(1.0);

			rig.ticks(2);

			assertEquals(0, rig.deployer.aliveCount(1));
			assertFalse(rig.groups().containsKey(1));
			assertEquals(5, rig.deployer.aliveCount(2));
		}
	}

	@Test
	@DisplayName("a stocked id that appears while another group exists is built before the group loop, so the tick cannot throw a CME")
	void reconcile_duringIteration_noCme() {
		try (Rig rig = new Rig(10)) {
			rig.ticks(1);
			List<Integer> many = new ArrayList<>();
			for (int id = 1; id <= 40; id++) {
				many.add(id);
				int turfId = id;
				when(rig.roster.garrisonOf(turfId)).thenAnswer(call -> rig.fresh(turfId));
				when(rig.roster.configOf(turfId)).thenAnswer(call -> rig.config);
				when(rig.roster.guardHealth(turfId)).thenReturn(1.0);
			}
			when(rig.roster.stockedTurfIds()).thenReturn(many);

			rig.ticks(1);

			assertEquals(3, rig.deployer.aliveCount(40));
		}
	}

	@Test
	@DisplayName("an unloaded area: no spawn, no spawn attempt, and the surface height is never read (it would load the chunk)")
	void unloadedSpot_noSpawn_noWarn_neverReadsHeight() {
		try (Rig rig = new Rig(10)) {
			when(rig.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);

			rig.ticks(3);

			assertEquals(0, rig.spawned.size());
			verify(rig.spawnManager, never()).spawnCivilian(any(), any());
			verify(rig.world, never()).getHighestBlockYAt(anyInt(), anyInt());
		}
	}

	@Test
	@DisplayName("one loaded chunk: exactly one guard, at that spot, not five stacked on the only loaded corner")
	void oneLoadedSpot_oneGuard() {
		try (Rig rig = new Rig(10)) {
			// the third post of a 0..40 region is at block (2, 38), chunk (0, 2)
			rig.loaded((x, z) -> x == 0 && z == 2);

			rig.ticks(4);

			assertEquals(1, rig.spawned.size());
			assertEquals(1, rig.alive());
			assertEquals(2.5, rig.spawnedAt.get(0).getX());
			assertEquals(38.5, rig.spawnedAt.get(0).getZ());
		}
	}

	@Test
	@DisplayName("a contest with no loaded spot does not throw, and a later turf still ticks")
	void contestWithNoLoadedSpot_doesNotThrow() {
		try (Rig rig = new Rig(10)) {
			// turf 1 sits at 0..40; turf 2 is the only loaded one, far away
			rig.loaded((x, z) -> x >= 60);
			rig.contest();
			when(rig.roster.stockedTurfIds()).thenReturn(List.of(1, 2));
			CuboidRegion far = new CuboidRegion("world", 1000, 1000, 1040, 1040);
			when(rig.roster.garrisonOf(2)).thenAnswer(call -> rig.fresh(2, far));
			when(rig.roster.configOf(2)).thenAnswer(call -> rig.config);
			when(rig.roster.guardHealth(2)).thenReturn(1.0);

			rig.ticks(2);

			assertEquals(0, rig.deployer.aliveCount(1));
			assertEquals(5, rig.deployer.aliveCount(2));
		}
	}

	@Test
	@DisplayName("I5: a guard that vanishes costs nothing and starts no cooldown; its slot refills on the next tick")
	void vanish_noDebit_noCooldown_slotRefills() {
		try (Rig rig = new Rig(10)) {
			rig.ticks(2);
			when(rig.spawned.get(1).isValid()).thenReturn(false);

			rig.ticks(1);

			assertEquals(10, rig.stock.get());
			assertEquals(-1L, rig.garrison().lastLossAt);
			assertEquals(6, rig.spawned.size());
			assertEquals(5, rig.alive());
		}
	}

	// ---- The debit ----

	@Test
	@DisplayName("a contest kill debits at death, and a second report of the same death does not")
	void contestKill_debitsExactlyOnce() {
		try (Rig rig = new Rig(10)) {
			rig.contest();
			rig.ticks(1);
			CivilianNpc dying = rig.spawned.get(0);

			rig.deployer.markKilled(dying);
			rig.deployer.markKilled(dying);

			assertEquals(9, rig.stock.get());
		}
	}

	@Test
	@DisplayName("ruling 1: a peace kill costs no guard, and the replacement waits out Wave_Cooldown_Seconds")
	void peaceKill_noDebit_replacedAfterCooldown() {
		try (Rig rig = new Rig(10)) {
			rig.ticks(2);
			CivilianNpc dying = rig.spawned.get(0);

			rig.deployer.markKilled(dying);
			when(dying.isValid()).thenReturn(false);
			rig.ticks(3);

			assertEquals(10, rig.stock.get());
			assertEquals(4, rig.alive());
			assertEquals(5, rig.spawned.size(), "no replacement before the cooldown");
			assertTrue(rig.garrison().lastLossAt >= 0);

			rig.garrison().lastLossAt -= 20_000L;
			rig.ticks(1);

			assertEquals(5, rig.alive());
			assertEquals(6, rig.spawned.size(), "one replacement at 20 s");
			assertEquals(10, rig.stock.get());
		}
	}

	@Test
	@DisplayName("ruling 1: a mob or a fall kills a standing guard: no debit, through the real death listener")
	void peaceMobDeath_noDebit() {
		try (Rig rig = new Rig(10)) {
			rig.ticks(2);
			TurfDefenderDeathListener listener = new TurfDefenderDeathListener(rig.deployer, new GangMembership(),
					mock(MoneyAddon.class), mock(MoneyDepositService.class));

			listener.onCivilianDeath(new CivilianDeathEvent(rig.spawned.get(0), null, 0.0));

			assertEquals(10, rig.stock.get());
		}
	}

	@Test
	@DisplayName("the debit and the bounty read the same flag at the same death: before standDown both, after it neither")
	void killAtContestBoundary_bountyAndDebitAgree() {
		try (Rig rig = new Rig(10)) {
			rig.contest();
			rig.ticks(1);
			CivilianNpc before = rig.spawned.get(0);
			CivilianNpc after  = rig.spawned.get(1);

			rig.deployer.markKilled(before);
			assertEquals(9, rig.stock.get());
			assertNotNull(rig.deployer.bountyOf(before));

			rig.deployer.standDown(1, true);

			rig.deployer.markKilled(after);
			assertEquals(9, rig.stock.get());
			assertNull(rig.deployer.bountyOf(after));
		}
	}

	@Test
	@DisplayName("a clean stop recalls every guard and leaves the count alone")
	void stop_leavesCountUnchanged() {
		try (Rig rig = new Rig(10)) {
			rig.ticks(2);
			assertEquals(5, rig.spawned.size());

			rig.deployer.stop();

			assertEquals(10, rig.stock.get());
			for (CivilianNpc guard : rig.spawned) {
				verify(guard).markForRemoval();
			}
		}
	}

	// ---- The contest transition ----

	@Test
	@DisplayName("adoption: a contest keeps the 5 standing bodies, adds exactly 1, and half-guards counts from the 5")
	void beginContest_sameBodies_noDoubleSpawn_resetsDeployed() throws Exception {
		try (Rig rig = new Rig(10)) {
			rig.ticks(2);
			List<CivilianNpc> standing = new ArrayList<>(rig.spawned);
			assertEquals(5, standing.size());

			rig.contest();
			assertEquals(5, rig.groupInt("deployed"));
			rig.ticks(1);

			assertEquals(6, rig.spawned.size());
			assertEquals(6, rig.alive());
			for (CivilianNpc guard : standing) {
				verify(guard, never()).markForRemoval();
			}
		}
	}

	@Test
	@DisplayName("a contest raises the target from Standing_Cap to Max_Alive")
	void beginContest_raisesTargetToMaxAlive() {
		try (Rig rig = new Rig(10)) {
			rig.ticks(2);
			assertEquals(5, rig.alive());

			rig.contest();
			rig.ticks(2);

			assertEquals(6, rig.alive());
		}
	}

	@Test
	@DisplayName("a contest at stock 0 keeps its group, so a guard bought mid-contest fights and pays a bounty")
	void beginContest_atStockZero_midContestBuyFights() {
		try (Rig rig = new Rig(0)) {
			rig.contest();
			rig.ticks(2);
			assertTrue(rig.groups().containsKey(1), "the group survives at stock 0");

			rig.stock.set(1);
			rig.ticks(1);

			assertEquals(1, rig.spawned.size());
			TurfDefenderDeployer.GuardBounty bounty = rig.deployer.bountyOf(rig.spawned.get(0));
			assertNotNull(bounty);
			assertEquals(500, bounty.amount());
		}
	}

	@Test
	@DisplayName("standDown: the guards stay, nothing is refunded and the challengers are cleared")
	void standDown_guardsStay_noRefund_challengersCleared() throws Exception {
		try (Rig rig = new Rig(10)) {
			UUID rival = UUID.randomUUID();
			rig.contest(rival);
			rig.ticks(2);
			assertEquals(6, rig.alive());

			rig.deployer.standDown(1, true);

			assertEquals(6, rig.alive());
			assertEquals(10, rig.stock.get());
			assertFalse(rig.contested());
			assertTrue(rig.groupChallengers().isEmpty());
			for (CivilianNpc guard : rig.spawned) {
				verify(guard, never()).markForRemoval();
			}
		}
	}

	@Test
	@DisplayName("standDown clears both targets, so neither a remembered rival nor a remembered cop re-engages")
	void standDown_clearsEntityTarget() {
		try (Rig rig = new Rig(3)) {
			rig.contest();
			rig.ticks(1);
			CivilianNpc guard = rig.spawned.get(0);

			rig.deployer.standDown(1, true);

			verify(guard).setTargetPlayerId(null);
			verify(guard).setTargetEntity(null);
		}
	}

	@Test
	@DisplayName("surplus leaves only after the delay, highest number first, and the count does not change")
	void standDown_trimsSurplusAfterDelay_highestNumbersFirst() {
		try (Rig rig = new Rig(10)) {
			rig.ticks(2);
			rig.contest();
			rig.ticks(1);
			assertEquals(6, rig.alive());

			rig.deployer.standDown(1, true);
			rig.ticks(2);
			assertEquals(6, rig.alive(), "everyone stays through the delay");

			rig.garrison().standDownAt = 0L;
			rig.ticks(2);

			assertEquals(5, rig.alive());
			assertEquals(10, rig.stock.get());
			verify(rig.spawned.get(5)).markForRemoval();
			for (int i = 0; i < 5; i++) {
				verify(rig.spawned.get(i), never()).markForRemoval();
			}
		}
	}

	@Test
	@DisplayName("a guard numbered past the peace target gives up its place: contest 1-6, number 2 killed, then 1-5 stand one per post")
	void standDown_highNumberRetires_freeLowNumberRefillsItsPost() {
		try (Rig rig = new Rig(10)) {
			rig.ticks(2);
			rig.contest();
			rig.ticks(1);
			assertEquals(6, rig.spawned.size());

			rig.deployer.markKilled(rig.spawned.get(1));
			when(rig.spawned.get(1).isValid()).thenReturn(false);
			rig.ticks(1);
			assertEquals(9, rig.stock.get());

			rig.deployer.standDown(1, true);
			rig.garrison().standDownAt = 0L;
			rig.garrison().lastLossAt -= 20_000L;
			rig.ticks(2);

			assertEquals(7, rig.spawned.size(), "number 2 comes back");
			verify(rig.spawned.get(5)).markForRemoval();
			assertEquals(5, rig.alive());
			assertEquals(9, rig.stock.get());
			assertEquals(rig.spawnedAt.get(1).getX(), rig.spawnedAt.get(6).getX());
			assertEquals(rig.spawnedAt.get(1).getZ(), rig.spawnedAt.get(6).getZ());
			Set<String> posts = new HashSet<>();
			for (int i : List.of(0, 2, 3, 4, 6)) {
				verify(rig.spawned.get(i), never()).markForRemoval();
				posts.add(rig.spawnedAt.get(i).getX() + "," + rig.spawnedAt.get(i).getZ());
			}
			assertEquals(5, posts.size(), "one guard per post");
		}
	}

	@Test
	@DisplayName("a turf's guards share one squads provider across a contest start, so a standing guard and a top-up rally together")
	void contest_standingAndTopUpShareOneSquadsProvider() {
		try (Rig rig = new Rig(10)) {
			rig.ticks(2);
			rig.contest();
			rig.ticks(1);
			assertEquals(6, rig.spawned.size());

			assertSame(lastSquads(rig.spawned.get(5)), lastSquads(rig.spawned.get(0)));
		}
	}

	@Test
	@DisplayName("a contest start forgets a visibility wait left by a contest that ended during it")
	void contest_resetsDelayedSince() {
		try (Rig rig = new Rig(10)) {
			rig.ticks(1);
			rig.garrison().delayedSince = 5L;

			rig.contest();

			assertEquals(-1L, rig.garrison().delayedSince);
		}
	}

	@Test
	@DisplayName("turf delete: at stock 0 the delay is skipped, the guards leave on the next tick and the group is dropped")
	void standDown_stockZero_trimsAtOnce() {
		try (Rig rig = new Rig(3)) {
			rig.contest();
			rig.ticks(1);
			assertEquals(3, rig.alive());

			rig.deployer.standDown(1, true);
			rig.stock.set(0);
			rig.ticks(1);

			assertEquals(0, rig.alive());
			assertFalse(rig.groups().containsKey(1));
			for (CivilianNpc guard : rig.spawned) {
				verify(guard).markForRemoval();
			}
		}
	}

	@Test
	@DisplayName("an admin lowering the stock in peace trims the highest numbers with no debit")
	void adminLowers_peace_trimsWithoutDebit() {
		try (Rig rig = new Rig(10)) {
			rig.ticks(2);
			assertEquals(5, rig.alive());

			rig.stock.set(2);
			rig.ticks(1);

			assertEquals(2, rig.alive());
			assertEquals(2, rig.stock.get());
			verify(rig.spawned.get(0), never()).markForRemoval();
			verify(rig.spawned.get(1), never()).markForRemoval();
			verify(rig.spawned.get(2)).markForRemoval();
			verify(rig.spawned.get(3)).markForRemoval();
			verify(rig.spawned.get(4)).markForRemoval();
		}
	}

	@Test
	@DisplayName("I3: stock lowered during a contest removes nobody")
	void noTrimDuringContest() {
		try (Rig rig = new Rig(10)) {
			rig.contest();
			rig.ticks(2);
			assertEquals(6, rig.alive());

			rig.stock.set(2);
			rig.ticks(2);

			assertEquals(6, rig.alive());
			for (CivilianNpc guard : rig.spawned) {
				verify(guard, never()).markForRemoval();
			}
		}
	}

	@Test
	@DisplayName("composition survives churn: guard 2's replacement takes number 2 and its role, in peace and in a contest")
	void replacement_reusesLowestFreeNumberAndRole() {
		try (Rig rig = new Rig(10)) {
			NpcRole second = role("B");
			rig.roles = Map.of("A", role("A"), "B", second, "C", role("C"));
			rig.config = config(5, 6, 3, 20, 8, new GarrisonComposition(List.of("A", "B", "C")));
			rig.ticks(2);
			assertEquals(5, rig.spawned.size());
			verify(rig.spawned.get(1)).applyRole(second);

			rig.deployer.markKilled(rig.spawned.get(1));
			when(rig.spawned.get(1).isValid()).thenReturn(false);
			rig.ticks(1);
			rig.garrison().lastLossAt -= 20_000L;
			rig.ticks(1);

			assertEquals(6, rig.spawned.size());
			verify(rig.spawned.get(5)).applyRole(second);
			assertEquals(rig.spawnedAt.get(1).getX(), rig.spawnedAt.get(5).getX());
			assertEquals(rig.spawnedAt.get(1).getZ(), rig.spawnedAt.get(5).getZ());

			rig.contest();
			rig.ticks(1);
			assertEquals(7, rig.spawned.size(), "number 6 tops up");
			rig.deployer.markKilled(rig.spawned.get(5));
			when(rig.spawned.get(5).isValid()).thenReturn(false);
			rig.ticks(1);
			rig.garrison().lastLossAt -= 20_000L;
			rig.ticks(1);

			assertEquals(8, rig.spawned.size());
			verify(rig.spawned.get(7)).applyRole(second);
		}
	}

	@Test
	@DisplayName("T-882: the reap marks a guard even when it is already invalid, so a body Citizens respawns is still removed")
	void invalidGuardReaped_markedForRemoval() {
		try (Rig rig = new Rig(1)) {
			rig.ticks(1);
			CivilianNpc guard = rig.spawned.get(0);
			when(guard.isValid()).thenReturn(false);

			rig.ticks(1);

			verify(guard).markForRemoval();
		}
	}

	@Test
	@DisplayName("T-882: a recall marks a guard even when it is already invalid")
	void recall_marksInvalidGuards() {
		try (Rig rig = new Rig(1)) {
			rig.ticks(1);
			CivilianNpc guard = rig.spawned.get(0);
			when(guard.isValid()).thenReturn(false);

			rig.deployer.recall(1);

			verify(guard).markForRemoval();
		}
	}

	@Test
	@DisplayName("T-767, TF-36: the next tick uses the new Max_Alive from the roster")
	void liveConfig_readEachTick() {
		try (Rig rig = new Rig(12)) {
			rig.contest();
			rig.ticks(2);
			assertEquals(6, rig.alive());

			rig.config = config(5, 9, 3, 20, 8, GarrisonComposition.NONE);
			rig.ticks(1);

			assertEquals(9, rig.alive());
		}
	}

	@Test
	@DisplayName("T-767: a second startGarrison updates the bounty and the tag")
	void readopt_updatesPriceAndTag() {
		try (Rig rig = new Rig(3)) {
			rig.contest();
			rig.ticks(1);
			CivilianNpc guard = rig.spawned.get(0);
			assertEquals(500, rig.deployer.bountyOf(guard).amount());

			TurfGarrison again = new TurfGarrison(1, rig.region, rig.config, "NEW", rig.stock::get,
					n -> rig.stock.addAndGet(-n) + n, rig.notices::addAndGet);
			again.funded(7, 3000);
			rig.deployer.startGarrison(again, Set::of);

			assertEquals(1500, rig.deployer.bountyOf(guard).amount());
			assertEquals(7, rig.deployer.bountyOf(guard).ownerGangId());
			assertEquals("NEW", rig.garrison().tag);
		}
	}

	@Test
	@DisplayName("a stuck guard in peace is left alone; the same guard in a contest is recycled")
	void idle_recycleStuckDoesNotRun() {
		try (Rig rig = new Rig(5)) {
			rig.ticks(2);
			assertEquals(5, rig.spawned.size());
			CivilianNpc stuck = rig.spawned.get(0);
			when(stuck.millisUnreachable()).thenReturn(60_000L);

			rig.ticks(3);

			verify(stuck, never()).markForRemoval();
			assertEquals(5, rig.spawned.size());

			rig.contest();
			rig.ticks(1);

			verify(stuck).markForRemoval();
			assertEquals(6, rig.spawned.size());
			assertEquals(5, rig.alive());
		}
	}

	@Test
	@DisplayName("a guard already at home never navigates: no hold, arrive, release every 5 ticks")
	void idleAtHome_noNavigation() {
		try (Rig rig = new Rig(5)) {
			rig.ticks(2);
			assertEquals(5, rig.spawned.size());

			rig.ticks(4);

			for (CivilianNpc guard : rig.spawned) {
				verify(guard, never()).holdPost(any());
				verify(guard, never()).navigateTo(any());
			}
		}
	}

	@Test
	@DisplayName("a peace top-up tells the owners nothing; a contest wave does")
	void peaceTopUp_noWaveNotice() {
		try (Rig rig = new Rig(10)) {
			rig.ticks(2);
			assertEquals(5, rig.spawned.size());
			assertEquals(0, rig.notices.get());

			rig.contest();
			rig.ticks(1);

			assertEquals(1, rig.notices.get());
		}
	}

	@Test
	@DisplayName("the half-guards alert: a peace death fires no TurfDefenderDownEvent, a contest death does")
	void peaceDeath_noDownEvent_contestDeath_fires() {
		try (Rig rig = new Rig(10)) {
			rig.ticks(2);
			assertEquals(5, rig.spawned.size());
			rig.deployer.markKilled(rig.spawned.get(0));
			when(rig.spawned.get(0).isValid()).thenReturn(false);

			rig.ticks(1);

			verify(rig.bukkit.pluginManager(), never()).callEvent(any(TurfDefenderDownEvent.class));

			rig.contest();
			rig.deployer.markKilled(rig.spawned.get(1));
			when(rig.spawned.get(1).isValid()).thenReturn(false);
			rig.ticks(1);

			verify(rig.bukkit.pluginManager()).callEvent(any(TurfDefenderDownEvent.class));
		}
	}

	@Test
	@DisplayName("goHome: a guard in COMBAT is not yanked; an IDLE guard away from home walks to its own spot, not the nearest post")
	void defend_combatGuardNotYanked_idleWalksHome() {
		try (Rig rig = new Rig(10)) {
			rig.ticks(2);
			CivilianNpc guard = rig.spawned.get(0);
			// number 1's post is at (2.5, 2.5); it stands near post 4 at (38.5, 38.5)
			rig.move(guard, 37.0, 37.0);
			guard.transitionTo(CivilianState.COMBAT);

			rig.ticks(2);

			verify(guard, never()).holdPost(any());

			guard.transitionTo(CivilianState.IDLE);
			rig.ticks(1);

			ArgumentCaptor<NpcPost> post = ArgumentCaptor.forClass(NpcPost.class);
			verify(guard).holdPost(post.capture());
			assertEquals(2.5, post.getValue().anchor().getX());
			assertEquals(2.5, post.getValue().anchor().getZ());
		}
	}

	// ---- Guard Toughness (ruling 5) ----

	@Test
	@DisplayName("toughness comes after the role: max = min(1024, role max x m)")
	void toughness_appliedAfterRole_cappedAt1024() {
		try (Rig rig = new Rig(1)) {
			rig.roles = Map.of("A", role("A"));
			rig.config = config(5, 6, 3, 20, 8, new GarrisonComposition(List.of("A")));
			rig.spawnHealth = 20.0;
			rig.roleHealth = 40.0;
			rig.toughness = 1.5;

			rig.ticks(1);

			assertEquals(60.0, rig.maxOf(rig.spawned.get(0)));
		}
		try (Rig rig = new Rig(1)) {
			rig.roles = Map.of("A", role("A"));
			rig.config = config(5, 6, 3, 20, 8, new GarrisonComposition(List.of("A")));
			rig.spawnHealth = 20.0;
			rig.roleHealth = 500.0;
			rig.toughness = 3.0;

			rig.ticks(1);

			assertEquals(1024.0, rig.maxOf(rig.spawned.get(0)));
		}
	}

	@Test
	@DisplayName("ruling 5: a buff reaches every standing guard on the next tick, and a guard at half health stays at half")
	void toughness_reachesStandingGuards_keepsHealthShare() {
		try (Rig rig = new Rig(5)) {
			rig.spawnHealth = 20.0;
			rig.ticks(2);
			assertEquals(5, rig.spawned.size());
			CivilianNpc hurt = rig.spawned.get(0);
			rig.setHealth(hurt, 10.0);

			rig.toughness = 1.5;
			rig.ticks(1);

			for (CivilianNpc guard : rig.spawned) {
				assertEquals(30.0, rig.maxOf(guard));
			}
			assertEquals(15.0, rig.healthOf(hurt));
			assertEquals(30.0, rig.healthOf(rig.spawned.get(1)));
		}
	}

	@Test
	@DisplayName("ruling 5: when the buff ends max health falls back to the role's and a hurt guard lives")
	void toughness_fallsBack_neverKills() {
		try (Rig rig = new Rig(1)) {
			rig.spawnHealth = 20.0;
			rig.toughness = 1.5;
			rig.ticks(1);
			CivilianNpc guard = rig.spawned.get(0);
			assertEquals(30.0, rig.maxOf(guard));
			rig.setHealth(guard, 0.5);

			rig.toughness = 1.0;
			rig.ticks(1);

			assertEquals(20.0, rig.maxOf(guard));
			assertTrue(rig.healthOf(guard) > 0.0);
		}
	}

	// ---- PLAN "Review Focus" ----

	@Test
	@DisplayName("review 1: World.isChunkLoaded takes chunk coordinates, so a turf far from the origin spawns; block coordinates would never")
	void farRegion_spawnsOnlyWhenChunkCoordsLoaded() {
		CuboidRegion far = new CuboidRegion("world", 1000, 1000, 1040, 1040);
		Set<String> chunks = new HashSet<>();
		Set<String> blocks = new HashSet<>();
		for (GarrisonPosts.Post post : GarrisonPosts.posts(far, (x, z) -> 64)) {
			int x = (int) Math.floor(post.x());
			int z = (int) Math.floor(post.z());
			chunks.add((x >> 4) + "," + (z >> 4));
			blocks.add(x + "," + z);
		}
		try (Rig rig = new Rig(10, far)) {
			rig.loaded((x, z) -> chunks.contains(x + "," + z));

			rig.ticks(2);

			assertEquals(5, rig.spawned.size());
		}
		try (Rig rig = new Rig(10, far)) {
			rig.loaded((x, z) -> blocks.contains(x + "," + z));

			rig.ticks(2);

			assertEquals(0, rig.spawned.size());
		}
	}

	@Test
	@DisplayName("review 2: walking away and back five times leaves the count alone and never passes the cap")
	void vanishCycles_countUnchanged_neverPastCap() {
		try (Rig rig = new Rig(10)) {
			rig.ticks(2);
			assertEquals(5, rig.alive());
			for (int cycle = 0; cycle < 5; cycle++) {
				for (CivilianNpc guard : new ArrayList<>(rig.spawned)) {
					when(guard.isValid()).thenReturn(false);
				}
				for (int tick = 0; tick < 3; tick++) {
					rig.ticks(1);
					assertTrue(rig.alive() <= 5, "alive " + rig.alive() + " in cycle " + cycle);
					assertEquals(10, rig.stock.get());
				}
			}
			assertEquals(5, rig.alive());
			assertEquals(30, rig.spawned.size(), "each cycle replaces all five");
		}
	}

	@Test
	@DisplayName("review 3: after a contest neither a remembered rival nor a remembered cop pulls a guard back into COMBAT, and it holds its post")
	void standDown_thenIdleTicks_noReengage() {
		try (Rig rig = new Rig(3)) {
			UUID id = UUID.randomUUID();
			Player rival = mock(Player.class);
			when(rival.getUniqueId()).thenReturn(id);
			when(rival.getLocation()).thenReturn(new Location(rig.world, 5, 65, 5));
			when(rival.getEyeLocation()).thenReturn(new Location(rig.world, 5, 66.6, 5));
			when(rig.world.getPlayers()).thenReturn(List.of(rival));
			// stood up in peace: a rival with a clear line to every post would hold a contest wave back for 3 s
			rig.ticks(2);
			rig.contest(id);
			CivilianNpc guard = rig.spawned.get(0);
			guard.setTargetEntity(mock(LivingEntity.class));
			rig.ticks(1);
			assertEquals(id, guard.getTargetPlayerId());
			assertEquals(CivilianState.COMBAT, guard.getCurrentState());
			rig.move(guard, 20.0, 20.0);

			rig.deployer.standDown(1, true);
			rig.garrison().standDownAt = 0L;
			clearInvocations(guard);
			rig.ticks(4);

			assertNull(guard.getTargetPlayerId());
			assertNull(guard.getTargetEntity());
			verify(guard, never()).transitionTo(CivilianState.COMBAT);
			verify(guard, atLeastOnce()).holdPost(any());
		}
	}

	@Test
	@DisplayName("review 4: stack and expiry never drift: 30, 45, 30, 20 on a guard at 25% health, which keeps its share and lives")
	void toughness_stackExpireCycle_exactRoleHealth_hurtGuardLives() {
		try (Rig rig = new Rig(1)) {
			rig.spawnHealth = 20.0;
			rig.ticks(1);
			CivilianNpc guard = rig.spawned.get(0);
			rig.setHealth(guard, 5.0);
			double[][] steps = {{1.5, 30.0}, {2.25, 45.0}, {1.5, 30.0}, {1.0, 20.0}};

			for (double[] step : steps) {
				rig.toughness = step[0];
				rig.ticks(1);

				assertEquals(step[1], rig.maxOf(guard));
				assertEquals(step[1] * 0.25, rig.healthOf(guard), 1e-9);
			}
			verify(guard.getEntity(), never()).setHealth(0.0);
		}
	}

	@Test
	@DisplayName("review 5: a guard answering a cop in peace is not yanked home mid-fight, and walks home once detached")
	void peaceCopAnswer_notYankedHome_walksHomeAfterDetach() {
		try (Rig rig = new Rig(5)) {
			rig.ticks(2);
			CivilianNpc guard = rig.spawned.get(0);
			LivingEntity cop = mock(LivingEntity.class);
			rig.move(guard, 20.0, 12.0);
			// what TurfCopGuard does: the cop goes to the front of the queue, then COMBAT
			guard.setTargetEntity(cop);
			guard.transitionTo(CivilianState.COMBAT);
			clearInvocations(guard);

			rig.ticks(3);

			verify(guard, never()).holdPost(any());
			verify(guard, never()).setTargetPlayerId(any());
			verify(guard, never()).setTargetEntity(any());

			guard.transitionTo(CivilianState.IDLE);
			rig.ticks(1);

			verify(guard).holdPost(any());
		}
	}

	// ---- Fixture ----

	/**
	 * THE MOCK TRAP (DESIGN 9): a mocked {@code World.isChunkLoaded} answers false and {@code Bukkit.getWorld} null, so a
	 * spawning test passes with nothing spawned. Every spawning test goes through this rig, which stubs both and hands out
	 * a fresh guard per spawn, and asserts a spawn count.
	 */
	private static final class Rig implements AutoCloseable {

		final BukkitStatics        bukkit       = BukkitStatics.install();
		final World                world        = mock(World.class);
		final CivilianSpawnManager spawnManager = mock(CivilianSpawnManager.class);
		final CivilianService      service      = mock(CivilianService.class);
		final GarrisonRoster       roster       = mock(GarrisonRoster.class);
		final TurfDefenderDeployer deployer     = new TurfDefenderDeployer(mock(JavaPlugin.class), service, spawnManager);
		final AtomicInteger        stock        = new AtomicInteger();
		final AtomicInteger        notices      = new AtomicInteger();
		final List<CivilianNpc>    spawned      = new ArrayList<>();
		final List<Location>       spawnedAt    = new ArrayList<>();
		final CuboidRegion         region;

		TurfDefenderConfig   config      = config(5, 6, 3, 20, 8, GarrisonComposition.NONE);
		Map<String, NpcRole> roles       = Map.of();
		double               toughness   = 1.0;
		double               spawnHealth = -1.0;
		double               roleHealth  = -1.0;

		Rig(int stock) {
			this(stock, new CuboidRegion("world", 0, 0, 40, 40));
		}

		Rig(int stock, CuboidRegion region) {
			this.region = region;
			this.stock.set(stock);
			bukkit.statics().when(() -> Bukkit.getWorld("world")).thenReturn(world);
			when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
			when(world.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(64);
			when(spawnManager.spawnCivilian(any(), eq("turf_defender"))).thenAnswer(call -> {
				Location    at    = call.getArgument(0);
				CivilianNpc guard = guardAt(at);
				CivilianAIBehaviorConfig ai = mock(CivilianAIBehaviorConfig.class);
				when(ai.roles()).thenReturn(roles);
				CivilianTypeConfig type = mock(CivilianTypeConfig.class);
				when(type.ai()).thenReturn(ai);
				when(guard.getTypeConfig()).thenReturn(type);
				if (spawnHealth > 0) {
					attachHealth(guard, spawnHealth);
				}
				if (roleHealth > 0) {
					doAnswer(applied -> {
						attribute(guard).setBaseValue(roleHealth);
						return null;
					}).when(guard).applyRole(any());
				}
				spawned.add(guard);
				spawnedAt.add(at);
				return guard;
			});
			when(roster.stockedTurfIds()).thenAnswer(call -> this.stock.get() > 0 ? List.of(1) : List.of());
			when(roster.garrisonOf(1)).thenAnswer(call -> fresh(1));
			when(roster.configOf(1)).thenAnswer(call -> config);
			when(roster.guardHealth(1)).thenAnswer(call -> toughness);
			deployer.setCitizensAvailable(() -> true);
			deployer.bindRoster(roster);
		}

		TurfGarrison fresh(int turfId) {
			return fresh(turfId, region);
		}

		TurfGarrison fresh(int turfId, CuboidRegion at) {
			TurfGarrison garrison = new TurfGarrison(turfId, at, config, "VIP", stock::get,
					n -> stock.addAndGet(-n) + n, notices::addAndGet);
			garrison.funded(5, 1000);
			return garrison;
		}

		void loaded(BiPredicate<Integer, Integer> chunks) {
			when(world.isChunkLoaded(anyInt(), anyInt())).thenAnswer(
					call -> chunks.test(call.<Integer>getArgument(0), call.<Integer>getArgument(1)));
		}

		void contest(UUID... challengers) {
			Set<UUID> ids = Set.of(challengers);
			deployer.startGarrison(fresh(1), () -> ids);
		}

		void ticks(int n) {
			for (int i = 0; i < n; i++) {
				deployer.tick();
			}
		}

		int alive() {
			return deployer.aliveCount(1);
		}

		void move(CivilianNpc guard, double x, double z) {
			when(guard.getEntity().getLocation()).thenReturn(new Location(world, x, 65, z));
		}

		@SuppressWarnings("unchecked")
		Map<Integer, Object> groups() {
			try {
				Field field = TurfDefenderDeployer.class.getDeclaredField("byTurfId");
				field.setAccessible(true);
				return (Map<Integer, Object>) field.get(deployer);
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
		}

		Object groupField(String name) {
			try {
				Object group = groups().get(1);
				Field  field = group.getClass().getDeclaredField(name);
				field.setAccessible(true);
				return field.get(group);
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
		}

		TurfGarrison garrison() {
			return (TurfGarrison) groupField("garrison");
		}

		boolean contested() {
			return (boolean) groupField("contested");
		}

		int groupInt(String name) {
			return (int) groupField(name);
		}

		@SuppressWarnings("unchecked")
		Set<UUID> groupChallengers() {
			return ((java.util.function.Supplier<Set<UUID>>) groupField("challengerMembers")).get();
		}

		double maxOf(CivilianNpc guard) {
			return attribute(guard).getBaseValue();
		}

		double healthOf(CivilianNpc guard) {
			return guard.getEntity().getHealth();
		}

		void setHealth(CivilianNpc guard, double health) {
			guard.getEntity().setHealth(health);
		}

		@Override
		public void close() {
			bukkit.close();
		}
	}

	private static TurfDefenderConfig config(int standingCap, int maxAlive, int waveSize, int cooldown, int standDown,
	                                         GarrisonComposition composition) {
		return new TurfDefenderConfig("turf_defender", 32.0, waveSize, maxAlive, cooldown, standDown, 16, composition, 0.5,
		                              12, 6.0, standingCap);
	}

	private static NpcRole role(String name) {
		return new NpcRole(name, name, NpcFanPlacement.ANY, null, null, 1.0, 0, null, 1.0, 0, null, 0, 0, false, false);
	}

	private static AttributeInstance attribute(CivilianNpc guard) {
		return guard.getEntity().getAttribute(XAttribute.MAX_HEALTH.get());
	}

	/** A max-health attribute and a current health that follow their setters, like the real thing. */
	private static void attachHealth(CivilianNpc guard, double maxHealth) {
		LivingEntity      body = guard.getEntity();
		AttributeInstance attr = mock(AttributeInstance.class);
		double[]          max  = {maxHealth};
		double[]          now  = {maxHealth};
		when(attr.getBaseValue()).thenAnswer(call -> max[0]);
		doAnswer(call -> {
			max[0] = call.getArgument(0);
			return null;
		}).when(attr).setBaseValue(anyDouble());
		when(body.getAttribute(XAttribute.MAX_HEALTH.get())).thenReturn(attr);
		when(body.getHealth()).thenAnswer(call -> now[0]);
		doAnswer(call -> {
			now[0] = call.getArgument(0);
			return null;
		}).when(body).setHealth(anyDouble());
	}

	/** A live guard with a body at {@code at}; its removal mark, state and targets follow their setters. */
	/** The squads provider this guard was last given. */
	private static FactionSquads lastSquads(CivilianNpc guard) {
		ArgumentCaptor<FactionSquads> captor = ArgumentCaptor.forClass(FactionSquads.class);
		verify(guard, atLeastOnce()).setFactionSquads(captor.capture());
		return captor.getValue();
	}

	private static CivilianNpc guardAt(Location at) {
		CivilianNpc  npc  = liveNpc();
		LivingEntity body = mock(LivingEntity.class);
		when(body.getUniqueId()).thenReturn(UUID.randomUUID());
		when(body.getWorld()).thenReturn(at.getWorld());
		when(body.getLocation()).thenReturn(at);
		when(body.getEyeLocation()).thenReturn(at);
		when(npc.getEntity()).thenReturn(body);

		AtomicReference<CivilianState> state = new AtomicReference<>(CivilianState.IDLE);
		when(npc.getCurrentState()).thenAnswer(call -> state.get());
		doAnswer(call -> {
			state.set(call.getArgument(0));
			return null;
		}).when(npc).transitionTo(any());

		AtomicReference<UUID> player = new AtomicReference<>();
		when(npc.getTargetPlayerId()).thenAnswer(call -> player.get());
		doAnswer(call -> {
			player.set(call.getArgument(0));
			return null;
		}).when(npc).setTargetPlayerId(any());

		AtomicReference<LivingEntity> entity = new AtomicReference<>();
		when(npc.getTargetEntity()).thenAnswer(call -> entity.get());
		doAnswer(call -> {
			entity.set(call.getArgument(0));
			return null;
		}).when(npc).setTargetEntity(any());
		return npc;
	}

	/** A live guard whose removal mark follows {@code markForRemoval}, as the reap and the trim rely on. */
	private static CivilianNpc liveNpc() {
		CivilianNpc npc = mock(CivilianNpc.class);
		when(npc.isValid()).thenReturn(true);
		boolean[] marked = {false};
		when(npc.isMarkedForRemoval()).thenAnswer(call -> marked[0]);
		doAnswer(call -> {
			marked[0] = true;
			return null;
		}).when(npc).markForRemoval();
		return npc;
	}

	private static TurfDefenderDeployer newDeployer() {
		return new TurfDefenderDeployer(mock(JavaPlugin.class), mock(CivilianService.class),
				mock(CivilianSpawnManager.class));
	}

	private static void putDefender(TurfDefenderDeployer deployer, int turfId, CivilianNpc npc) {
		deployer.track(turfId, npc);
	}
}
