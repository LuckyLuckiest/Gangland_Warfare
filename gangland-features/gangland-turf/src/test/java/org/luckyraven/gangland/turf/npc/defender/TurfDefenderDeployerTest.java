package org.luckyraven.gangland.turf.npc.defender;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.events.CivilianDeathEvent;
import org.luckyraven.gangland.civilians.listener.civilian.CivilianDeathListener;
import org.luckyraven.gangland.civilians.npc.CivilianService;
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
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;
import org.bukkit.event.Event;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
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
				new TurfDefenderConfig("turf_defender", 32.0, 600), "VIP", () -> 5, n -> {
			consumed.addAndGet(n);
			return n;
		}, n -> {
		});

		deployer.startGarrison(garrison, Set::of, () -> 600);
		deployer.tick();

		assertEquals(0, consumed.get());
		assertEquals(0, deployer.aliveCount(1));
		verifyNoInteractions(spawnManager);
	}

	@Test
	@DisplayName("consume after spawn: the reserve pays one per guard that spawned, a failed spawn costs nothing")
	void wave_consumesOnlyAfterSpawn() {
		CivilianSpawnManager spawnManager = mock(CivilianSpawnManager.class);
		CivilianNpc first  = liveNpc();
		CivilianNpc second = liveNpc();
		when(spawnManager.spawnCivilian(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("turf_defender")))
				.thenReturn(first, second, null);
		TurfDefenderDeployer deployer = new TurfDefenderDeployer(mock(JavaPlugin.class), mock(CivilianService.class),
				spawnManager);
		deployer.setCitizensAvailable(() -> true);
		AtomicInteger stock  = new AtomicInteger(10);
		AtomicInteger notice = new AtomicInteger();
		TurfGarrison garrison = new TurfGarrison(1, new CuboidRegion("world", 0, 0, 20, 20),
				new TurfDefenderConfig("turf_defender", 32.0, 600), "VIP", stock::get,
				n -> stock.addAndGet(-n) + n, notice::addAndGet);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			World world = mock(World.class);
			bukkit.statics().when(() -> Bukkit.getWorld("world")).thenReturn(world);

			deployer.startGarrison(garrison, Set::of, () -> 600);
			assertEquals(10, stock.get(), "arming the garrison spends nothing");
			deployer.tick();
		}

		// the wave asked for 3: two spawned, the third came back null
		assertEquals(8, stock.get());
		assertEquals(2, notice.get());
		assertEquals(2, deployer.aliveCount(1));
	}

	@Test
	@DisplayName("a challenger far outside the leash is ignored: no ray is traced for it")
	void farChallenger_causesNoRayTrace() {
		CivilianSpawnManager spawnManager = mock(CivilianSpawnManager.class);
		TurfDefenderDeployer deployer = new TurfDefenderDeployer(mock(JavaPlugin.class), mock(CivilianService.class),
				spawnManager);
		deployer.setCitizensAvailable(() -> true);
		TurfGarrison garrison = new TurfGarrison(1, new CuboidRegion("world", 0, 0, 20, 20),
				new TurfDefenderConfig("turf_defender", 32.0, 600), "VIP", () -> 5, n -> n, n -> {
		});
		UUID id = UUID.randomUUID();
		org.bukkit.entity.Player far = mock(org.bukkit.entity.Player.class);
		when(far.getUniqueId()).thenReturn(id);
		when(far.getEyeLocation()).thenReturn(new org.bukkit.Location(null, 2000, 70, 2000));

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			World world = mock(World.class);
			when(world.getPlayers()).thenReturn(java.util.List.of(far));
			bukkit.statics().when(() -> Bukkit.getWorld("world")).thenReturn(world);

			deployer.startGarrison(garrison, () -> Set.of(id), () -> 600);
			deployer.tick();

			verify(world, org.mockito.Mockito.never()).rayTraceBlocks(org.mockito.ArgumentMatchers.any(org.bukkit.Location.class),
					org.mockito.ArgumentMatchers.any(org.bukkit.util.Vector.class), org.mockito.ArgumentMatchers.anyDouble(),
					org.mockito.ArgumentMatchers.any(org.bukkit.FluidCollisionMode.class), org.mockito.ArgumentMatchers.anyBoolean());
		}
	}

	@Test
	@DisplayName("F-GARRISON r1: standDown while a rival is still on the turf, then a restart re-arms the same guards: no return, no removal")
	void standDown_rivalStillOnTurf_restartRearms() {
		TurfDefenderDeployer deployer = newDeployer();
		deployer.setCitizensAvailable(() -> true);
		UUID id = UUID.randomUUID();
		org.bukkit.entity.Player rival = mock(org.bukkit.entity.Player.class);
		when(rival.isOnline()).thenReturn(true);
		when(rival.getLocation()).thenReturn(new org.bukkit.Location(null, 5, 70, 5));
		CivilianNpc guard = liveNpc();
		AtomicInteger returned = new AtomicInteger(-1);
		TurfGarrison garrison = new TurfGarrison(1, new CuboidRegion("world", 0, 0, 20, 20),
				new TurfDefenderConfig("turf_defender", 32.0, 600), "VIP", () -> 0, n -> n, n -> {
		});

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(rival);
			deployer.startGarrison(garrison, () -> Set.of(id), () -> 600);
			deployer.track(1, guard, System.currentTimeMillis(), () -> 600);

			deployer.standDown(1, true, returned::set);
			assertEquals(true, garrison.standingDown, "standDown runs even with a rival on the turf");

			deployer.startGarrison(new TurfGarrison(1, new CuboidRegion("world", 0, 0, 20, 20),
					new TurfDefenderConfig("turf_defender", 32.0, 600), "VIP", () -> 0, n -> n, n -> {
			}), () -> Set.of(id), () -> 600);
			assertEquals(false, garrison.standingDown, "the restart re-arms the garrison");
			deployer.tick();
			assertEquals(-1, returned.get(), "onReturn is never called once the restart re-armed the guards");
			assertEquals(1, deployer.aliveCount(1), "the restarted contest keeps the standing guard");
			verify(guard, org.mockito.Mockito.never()).markForRemoval();
		}
	}

	@Test
	@DisplayName("F-GARRISON r1: standDown while a rival is still on the turf, with no restart, releases the group on the next tick past the window")
	void standDown_rivalStillOnTurf_noRestart_releasesOnTick() {
		TurfDefenderDeployer deployer = newDeployer();
		deployer.setCitizensAvailable(() -> true);
		UUID id = UUID.randomUUID();
		org.bukkit.entity.Player rival = mock(org.bukkit.entity.Player.class);
		when(rival.isOnline()).thenReturn(true);
		when(rival.getLocation()).thenReturn(new org.bukkit.Location(null, 5, 70, 5));
		CivilianNpc guard = liveNpc();
		AtomicInteger returned = new AtomicInteger(-1);
		TurfGarrison garrison = new TurfGarrison(1, new CuboidRegion("world", 0, 0, 20, 20),
				new TurfDefenderConfig("turf_defender", 32.0, 600, 3, 6, 20, 0, 16), "VIP", () -> 0, n -> n, n -> {
		});

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(rival);
			deployer.startGarrison(garrison, () -> Set.of(id), () -> 600);
			deployer.track(1, guard, System.currentTimeMillis(), () -> 600);

			deployer.standDown(1, true, returned::set);
			deployer.tick();
			assertEquals(1, returned.get(), "the survivor is handed back once the stand-down window passes");
		}
	}

	@Test
	@DisplayName("T-751: once the rival has left, standDown stands the guards down and hands the survivors back")
	void standDown_rivalGone_standsDown() {
		TurfDefenderDeployer deployer = newDeployer();
		deployer.setCitizensAvailable(() -> true);
		UUID id = UUID.randomUUID();
		CivilianNpc guard = liveNpc();
		TurfGarrison garrison = new TurfGarrison(1, new CuboidRegion("world", 0, 0, 20, 20),
				new TurfDefenderConfig("turf_defender", 32.0, 600), "VIP", () -> 0, n -> n, n -> {
		});

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(null);
			deployer.startGarrison(garrison, () -> Set.of(id), () -> 600);
			deployer.track(1, guard, System.currentTimeMillis(), () -> 600);

			deployer.standDown(1, true, n -> {
			});
			assertEquals(true, garrison.standingDown);
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
	@DisplayName("T-269: the lifespan is read on every tick, so a reload extends guards already in the field")
	void lifespanSupplier_isReadEachTick() {
		TurfDefenderDeployer deployer = newDeployer();
		CivilianNpc guard = liveNpc();
		AtomicInteger seconds = new AtomicInteger(0);
		putDefender(deployer, 1, guard, System.currentTimeMillis(), seconds::get);

		seconds.set(600);
		deployer.tick();

		assertEquals(1, deployer.aliveCount(1));
	}

	@Test
	@DisplayName("a guard removed because its lifespan ran out fires no TurfDefenderDownEvent")
	void expiredDefender_firesNothing() {
		TurfDefenderDeployer deployer = newDeployer();
		CivilianNpc expired = liveNpc();
		putDefender(deployer, 1, expired, System.currentTimeMillis() - 1_000L, () -> 0);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			deployer.tick();

			verify(bukkit.pluginManager(), org.mockito.Mockito.never()).callEvent(org.mockito.ArgumentMatchers.any(Event.class));
		}
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
				new TurfDefenderConfig("turf_defender", 32.0, 600, 3, 6, 20, 0, 16), "VIP", () -> 0, n -> n, n -> {
		});
		garrison.posts = List.of(POST);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(() -> Bukkit.getWorld("world")).thenReturn(world);
			deployer.startGarrison(garrison, () -> challengers.keySet(), () -> 600);
			deployer.track(1, guard, System.currentTimeMillis(), () -> 600);
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

	private static CivilianNpc liveNpc() {
		CivilianNpc npc = mock(CivilianNpc.class);
		when(npc.isValid()).thenReturn(true);
		return npc;
	}

	private static TurfDefenderDeployer newDeployer() {
		return new TurfDefenderDeployer(mock(JavaPlugin.class), mock(CivilianService.class),
				mock(CivilianSpawnManager.class));
	}

	private static void putDefender(TurfDefenderDeployer deployer, int turfId, CivilianNpc npc) {
		deployer.track(turfId, npc, System.currentTimeMillis(), () -> 1_000_000);
	}

	private static void putDefender(TurfDefenderDeployer deployer, int turfId, CivilianNpc npc, long deployedAt,
	                                IntSupplier lifespanSeconds) {
		deployer.track(turfId, npc, deployedAt, lifespanSeconds);
	}
}
