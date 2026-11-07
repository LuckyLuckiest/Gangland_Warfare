package org.luckyraven.gangland.npcshops.banker;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.MetadataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.npcshops.banker.config.BankerSettings;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterises {@link BankerManager#findTargetedBanker} (the ray trace) and the head-tracking tick (the closest
 * player lookup) before those two helpers are shared with {@code TraderManager}, and pins what
 * {@link BankerManager#create} returns (T-957).
 */
@DisplayName("BankerManager - targeting, head tracking and create")
class BankerManagerTargetingTest {

	@SuppressWarnings("unchecked")
	private final IRepository<BankerData> repository = mock(IRepository.class);
	private final JavaPlugin     plugin   = mock(JavaPlugin.class);
	private final BankerSettings settings = mock(BankerSettings.class);
	private final World          world    = mock(World.class);
	private final NPCRegistry    registry = mock(NPCRegistry.class);
	private final BankerManager  manager;

	BankerManagerTargetingTest() {
		manager = new BankerManager(plugin, repository, settings);
	}

	private record Spawned(BankerData data, NPC npc, Entity entity) { }

	private Spawned spawnBanker(double x) {
		BankerData data   = new BankerData(UUID.randomUUID(), new Location(world, x, 64, 0), null);
		NPC        npc    = mock(NPC.class);
		MetadataStore store = mock(MetadataStore.class);
		when(npc.data()).thenReturn(store);
		Entity     entity = mock(Entity.class);
		when(registry.createNPC(any(EntityType.class), anyString())).thenReturn(npc);
		when(npc.isSpawned()).thenReturn(true);
		when(npc.getEntity()).thenReturn(entity);
		when(entity.getLocation()).thenReturn(new Location(world, x, 64, 0));
		when(store.has(BankerNpc.METADATA_BANKER_ID)).thenReturn(true);
		when(store.<String>get(BankerNpc.METADATA_BANKER_ID)).thenReturn(data.getId().toString());
		when(registry.getNPC(entity)).thenReturn(npc);
		register(new BankerNpc(data, npc));
		return new Spawned(data, npc, entity);
	}

	/** BankerNpc.spawn needs a live Citizens SkinTrait, so the wrapper is put into the private byId map directly. */
	@SuppressWarnings("unchecked")
	private void register(BankerNpc npc) {
		try {
			Field field = BankerManager.class.getDeclaredField("byId");
			field.setAccessible(true);
			((Map<UUID, BankerNpc>) field.get(manager)).put(npc.getData().getId(), npc);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private Player playerAt(double x) {
		Player player = mock(Player.class);
		when(player.getLocation()).thenReturn(new Location(world, x, 64, 0));
		return player;
	}

	private interface Scenario {
		void run(BukkitScheduler scheduler) throws Exception;
	}

	private void withCitizens(Scenario scenario) throws Exception {
		try (BukkitStatics bukkit = BukkitStatics.install();
		     MockedStatic<NpcSupport> support = mockStatic(NpcSupport.class);
		     MockedStatic<CitizensAPI> citizens = mockStatic(CitizensAPI.class)) {
			support.when(NpcSupport::available).thenReturn(true);
			citizens.when(CitizensAPI::getNPCRegistry).thenReturn(registry);
			scenario.run(Bukkit.getScheduler());
		}
	}

	private Runnable headTrackTick(BukkitScheduler scheduler) {
		manager.onInitialize(true);
		ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
		verify(scheduler, atLeast(2)).runTaskTimer(eq(plugin), tasks.capture(), anyLong(), anyLong());
		return tasks.getAllValues().get(0);
	}

	@Test
	@DisplayName("findTargetedBanker traces from the eye along its direction, ignores the caller, and maps the hit")
	void findTargeted_tracesAndMapsTheHit() throws Exception {
		withCitizens(scheduler -> {
			Spawned  s      = spawnBanker(0);
			Player   player = mock(Player.class);
			Location eye    = new Location(world, 1, 65, 2, 90f, 10f);
			when(player.getEyeLocation()).thenReturn(eye);
			when(player.getWorld()).thenReturn(world);
			RayTraceResult hit = mock(RayTraceResult.class);
			when(hit.getHitEntity()).thenReturn(s.entity());
			when(world.rayTraceEntities(any(Location.class), any(Vector.class), anyDouble(), any())).thenReturn(hit);

			BankerNpc found = manager.findTargetedBanker(player, 7.5);

			assertSame(manager.get(s.data().getId()), found);
			@SuppressWarnings("unchecked")
			ArgumentCaptor<Predicate<Entity>> filter = ArgumentCaptor.forClass(Predicate.class);
			verify(world).rayTraceEntities(eq(eye), eq(eye.getDirection()), eq(7.5), filter.capture());
			assertFalse(filter.getValue().test(player));
			assertTrue(filter.getValue().test(mock(Entity.class)));
		});
	}

	@Test
	@DisplayName("findTargetedBanker is null for no hit, a hit without an entity, and a non-banker entity")
	void findTargeted_nullCases() throws Exception {
		withCitizens(scheduler -> {
			spawnBanker(0);
			Player player = mock(Player.class);
			when(player.getEyeLocation()).thenReturn(new Location(world, 0, 65, 0));
			when(player.getWorld()).thenReturn(world);

			when(world.rayTraceEntities(any(Location.class), any(Vector.class), anyDouble(), any())).thenReturn(null);
			assertNull(manager.findTargetedBanker(player, 5));

			RayTraceResult miss = mock(RayTraceResult.class);
			when(world.rayTraceEntities(any(Location.class), any(Vector.class), anyDouble(), any())).thenReturn(miss);
			assertNull(manager.findTargetedBanker(player, 5));

			Entity stranger = mock(Entity.class);
			when(miss.getHitEntity()).thenReturn(stranger);
			when(registry.getNPC(stranger)).thenReturn(null);
			assertNull(manager.findTargetedBanker(player, 5));
		});
	}

	@Test
	@DisplayName("head tracking faces the nearest player strictly inside the radius")
	void headTrack_facesNearestPlayerInsideRadius() throws Exception {
		withCitizens(scheduler -> {
			Spawned s = spawnBanker(0);
			when(settings.getHeadTrackRadius()).thenReturn(5);
			Player far  = playerAt(3);
			Player near = playerAt(1);
			Player out  = playerAt(5); // exactly on the radius: not strictly inside
			when(world.getPlayers()).thenReturn(List.of(far, out, near));

			headTrackTick(scheduler).run();

			verify(s.npc()).faceLocation(near.getLocation());
			verify(s.npc(), times(1)).faceLocation(any());
		});
	}

	@Test
	@DisplayName("head tracking keeps the first of two equally distant players and faces nobody outside the radius")
	void headTrack_tieKeepsFirst_andOutsideFacesNobody() throws Exception {
		withCitizens(scheduler -> {
			Spawned s = spawnBanker(0);
			when(settings.getHeadTrackRadius()).thenReturn(5);
			Player first  = playerAt(2);
			Player second = playerAt(-2);
			when(world.getPlayers()).thenReturn(List.of(first, second));

			Runnable tick = headTrackTick(scheduler);
			tick.run();
			verify(s.npc()).faceLocation(first.getLocation());
			verify(s.npc(), times(1)).faceLocation(any());

			Player onEdge = playerAt(5);
			Player distant = playerAt(9);
			when(world.getPlayers()).thenReturn(List.of(onEdge, distant));
			tick.run();
			verify(s.npc(), times(1)).faceLocation(any());
		});
	}

	@Test
	@DisplayName("head tracking skips a dead banker")
	void headTrack_skipsDeadBanker() throws Exception {
		withCitizens(scheduler -> {
			Spawned s = spawnBanker(0);
			when(settings.getHeadTrackRadius()).thenReturn(5);
			when(s.entity().isDead()).thenReturn(true);
			Player close = playerAt(1);
			when(world.getPlayers()).thenReturn(List.of(close));

			headTrackTick(scheduler).run();

			verify(s.npc(), never()).faceLocation(any());
		});
	}

	@Test
	@DisplayName("create saves the row and returns false when Citizens is absent, so nothing is reported as spawned (T-957)")
	void create_savesAndReturnsFalseWithoutCitizens() {
		try (MockedStatic<NpcSupport> support = mockStatic(NpcSupport.class)) {
			support.when(NpcSupport::available).thenReturn(false);
			BankerData data = new BankerData(UUID.randomUUID(), new Location(world, 0, 64, 0), null);

			assertFalse(manager.create(data));

			verify(repository).save(data);
			assertNull(manager.get(data.getId()));
		}
	}

	@Test
	@DisplayName("create saves the row and returns true when an NPC is standing for it (T-957)")
	void create_savesAndReturnsTrueWhenNpcStands() throws Exception {
		withCitizens(scheduler -> {
			Spawned s = spawnBanker(0);

			assertTrue(manager.create(s.data()));

			verify(repository).save(s.data());
		});
	}
}
