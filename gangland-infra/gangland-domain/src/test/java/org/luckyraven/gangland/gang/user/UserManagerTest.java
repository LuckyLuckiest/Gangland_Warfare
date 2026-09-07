package org.luckyraven.gangland.gang.user;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.gangland.inventory.service.InventoryRegistry;
import org.luckyraven.keystone.economy.bank.Bank;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.luckyraven.keystone.util.Placeholder;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pins {@link UserManager}'s cache semantics and the data-supplier collision the users-levels-economy-bank.md
 * Test Surface flags: "UserManager: supplier collisions between the two beans (Obs. #12) and a map-key test
 * asserting a Player-keyed entry is not retrievable via Bukkit.getOfflinePlayer(uuid)".
 *
 * <p>Observation #1 (users-levels-economy-bank.md) / US-01: the cache used to key on the Bukkit handle, so the
 * quit-time snapshot (added with a {@code Player}) was invisible to the join-time eviction (which looked up with a
 * fresh handle) and survived to overwrite the live row on the next autosave. These tests pin the uuid keying that
 * closes it: every lookup, eviction and containment check now agrees for any handle flavour carrying the same uuid.
 */
@DisplayName("UserManager - cache identity and shared-repository supplier collisions")
class UserManagerTest {

	@BeforeEach
	void bindSettings() {
		GangSettings.bind(new FakeGangSettingsContract());
	}

	@Test
	@DisplayName("US-01: getUser resolves through any Bukkit handle carrying the same uuid")
	void getUser_findsTheUserThroughAnyHandleWithTheSameUuid() {
		UserManager<OfflinePlayer> manager = newManager();

		UUID          sharedUuid       = UUID.randomUUID();
		OfflinePlayer handleUsedAtAdd  = handle(sharedUuid);
		User<OfflinePlayer> user       = newUser(handleUsedAtAdd);

		manager.add(user);

		OfflinePlayer differentHandleSameUuid = handle(sharedUuid);

		assertSame(user, manager.getUser(handleUsedAtAdd), "the handle used at add() still retrieves the user");
		assertSame(user, manager.getUser(differentHandleSameUuid),
				"Observation #1 (users-levels-economy-bank.md): the cache must key on the uuid, not on the "
				+ "Bukkit handle. CraftEntity.equals/hashCode compare the entity id while CraftOfflinePlayer "
				+ "compares the uuid, so a handle-keyed map could never find a Player-keyed entry through an "
				+ "OfflinePlayer handle - which is what made the join-time eviction miss.");
		assertSame(user, manager.getUser(sharedUuid), "the uuid overload finds the same entry");
		assertTrue(manager.contains(user));
	}

	@Test
	@DisplayName("US-01: the quit snapshot is evicted on rejoin even though quit stored a Player and join looks up "
	             + "with a fresh handle, so the stale row can no longer overwrite the live one on autosave")
	void quitSnapshot_isEvictedOnRejoin_acrossHandleFlavours() {
		UserManager<OfflinePlayer> offlineManager = newManager();

		UUID uuid = UUID.randomUUID();

		// PlayerQuitEvent path: RemoveAccountListener calls offlineUserManager.create(player) with the live
		// Player handle, so the snapshot lands under a Player-flavoured key.
		Player              quittingPlayer = mock(Player.class);
		when(quittingPlayer.getUniqueId()).thenReturn(uuid);
		User<OfflinePlayer> quitSnapshot   = newUser(quittingPlayer);
		offlineManager.add(quitSnapshot);

		assertEquals(1, offlineManager.size());

		// PlayerJoinEvent path: CreateAccountListener evicts by uuid.
		offlineManager.remove(uuid);

		assertEquals(0, offlineManager.size(), "the quit-time snapshot must not survive the rejoin");
		assertNull(offlineManager.getUser(uuid));
		assertTrue(offlineManager.getUsers().values().isEmpty(),
				"PeriodicalUpdates writes offline users after online users; a surviving snapshot would roll the "
				+ "live row back to its quit-time balance on the first autosave");
	}

	@Test
	@DisplayName("US-01: remove(User) also evicts when the caller holds a different handle for the same uuid")
	void removeUser_evictsAcrossHandleFlavours() {
		UserManager<OfflinePlayer> manager = newManager();

		UUID                uuid  = UUID.randomUUID();
		User<OfflinePlayer> added = newUser(handle(uuid));
		manager.add(added);

		User<OfflinePlayer> sameUuidDifferentHandle = newUser(handle(uuid));
		manager.remove(sameUuidDifferentHandle);

		assertEquals(0, manager.size());
	}

	@Test
	@DisplayName("remove(null uuid) is a no-op rather than an NPE")
	void removeNullUuid_isANoOp() {
		UserManager<OfflinePlayer> manager = newManager();

		assertNull(manager.remove((UUID) null));
		assertNull(manager.getUser((UUID) null));
	}

	private static UserManager<OfflinePlayer> newManager() {
		return new UserManager<>(mock(JavaPlugin.class), mock(RepositoryRegistry.class), mock(UserFactory.class));
	}

	private static OfflinePlayer handle(UUID uuid) {
		OfflinePlayer handle = mock(OfflinePlayer.class);
		when(handle.getUniqueId()).thenReturn(uuid);
		return handle;
	}

	private static <T extends OfflinePlayer> User<T> newUser(T handle) {
		return new User<>(mock(JavaPlugin.class), handle, mock(Placeholder.class), new InventoryRegistry());
	}

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("CL-23 / Observation #12: linked beans still overwrite each other's supplier on the shared "
	             + "repositories, but EVERY registered supplier now yields the union of both caches")
	void linkedUserManagerBeans_eitherSupplierPersistsBothCaches() {
		RepositoryRegistry registry = mock(RepositoryRegistry.class);
		IRepository<User<? extends OfflinePlayer>> userRepo = mock(IRepository.class);
		IRepository<Bank> bankRepo = mock(IRepository.class);
		when(registry.<User<? extends OfflinePlayer>>getGenericRepository(User.class)).thenReturn(userRepo);
		when(registry.getRepository(Bank.class)).thenReturn(bankRepo);

		JavaPlugin plugin = mock(JavaPlugin.class);
		Placeholder placeholder = mock(Placeholder.class);
		InventoryRegistry inventoryRegistry = new InventoryRegistry();
		UserFactory factory = mock(UserFactory.class);

		UserManager<Player> onlineManager = new UserManager<>(plugin, registry, factory);
		UserManager<OfflinePlayer> offlineManager = new UserManager<>(plugin, registry, factory);

		// DataConfig.offlineUserManager(...) wires exactly this link
		offlineManager.link(onlineManager);

		Player onlinePlayer = mock(Player.class);
		when(onlinePlayer.getUniqueId()).thenReturn(UUID.randomUUID());
		User<Player> onlineUser = new User<>(plugin, onlinePlayer, placeholder, inventoryRegistry);
		onlineManager.add(onlineUser);

		OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
		when(offlinePlayer.getUniqueId()).thenReturn(UUID.randomUUID());
		User<OfflinePlayer> offlineUser = new User<>(plugin, offlinePlayer, placeholder, inventoryRegistry);
		offlineManager.add(offlineUser);

		onlineManager.initialize();
		offlineManager.initialize();

		org.mockito.ArgumentCaptor<Supplier<Collection<User<? extends OfflinePlayer>>>> captor =
				org.mockito.ArgumentCaptor.forClass(Supplier.class);
		verify(userRepo, times(2)).setDataSupplier(captor.capture());

		java.util.List<Supplier<Collection<User<? extends OfflinePlayer>>>> registeredSuppliers = captor.getAllValues();

		// Whichever bean initialized last owns the repository — every registration must see both caches, so the
		// order of the two initialize() calls can no longer decide which cache survives saveAll().
		for (Supplier<Collection<User<? extends OfflinePlayer>>> supplier : registeredSuppliers) {
			Collection<User<? extends OfflinePlayer>> visibleToSaveAll = supplier.get();

			assertEquals(2, visibleToSaveAll.size());
			assertTrue(visibleToSaveAll.contains(onlineUser),
					"the online manager's cached user must stay visible to repositoryRegistry.saveAll()");
			assertTrue(visibleToSaveAll.contains(offlineUser),
					"the offline manager's cached user must stay visible to repositoryRegistry.saveAll()");
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("CL-23: an unlinked manager still supplies only its own cache")
	void unlinkedUserManager_suppliesOnlyItsOwnCache() {
		RepositoryRegistry registry = mock(RepositoryRegistry.class);
		IRepository<User<? extends OfflinePlayer>> userRepo = mock(IRepository.class);
		IRepository<Bank> bankRepo = mock(IRepository.class);
		when(registry.<User<? extends OfflinePlayer>>getGenericRepository(User.class)).thenReturn(userRepo);
		when(registry.getRepository(Bank.class)).thenReturn(bankRepo);

		JavaPlugin plugin = mock(JavaPlugin.class);
		Placeholder placeholder = mock(Placeholder.class);
		InventoryRegistry inventoryRegistry = new InventoryRegistry();
		UserFactory factory = mock(UserFactory.class);

		UserManager<OfflinePlayer> manager = new UserManager<>(plugin, registry, factory);

		OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
		when(offlinePlayer.getUniqueId()).thenReturn(UUID.randomUUID());
		User<OfflinePlayer> user = new User<>(plugin, offlinePlayer, placeholder, inventoryRegistry);
		manager.add(user);

		manager.initialize();

		org.mockito.ArgumentCaptor<Supplier<Collection<User<? extends OfflinePlayer>>>> captor =
				org.mockito.ArgumentCaptor.forClass(Supplier.class);
		verify(userRepo).setDataSupplier(captor.capture());

		Collection<User<? extends OfflinePlayer>> supplied = captor.getValue().get();

		assertEquals(1, supplied.size());
		assertTrue(supplied.contains(user));
	}

}
