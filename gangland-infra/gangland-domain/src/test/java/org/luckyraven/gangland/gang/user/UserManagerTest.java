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
 */
@DisplayName("UserManager - cache identity and shared-repository supplier collisions")
class UserManagerTest {

	@BeforeEach
	void bindSettings() {
		GangSettings.bind(new FakeGangSettingsContract());
	}

	@Test
	@DisplayName("getUser looks up by the exact object identity of T (HashMap default equals/hashCode)")
	void getUser_missesOnADifferentHandleForTheSameUuid() {
		RepositoryRegistry registry = mock(RepositoryRegistry.class);
		UserFactory factory = mock(UserFactory.class);
		JavaPlugin plugin = mock(JavaPlugin.class);
		UserManager<OfflinePlayer> manager = new UserManager<>(plugin, registry, factory);

		UUID sharedUuid = UUID.randomUUID();
		OfflinePlayer handleUsedAtAdd = mock(OfflinePlayer.class);
		when(handleUsedAtAdd.getUniqueId()).thenReturn(sharedUuid);
		Placeholder placeholder = mock(Placeholder.class);
		InventoryRegistry inventoryRegistry = new InventoryRegistry();
		User<OfflinePlayer> user = new User<>(plugin, handleUsedAtAdd, placeholder, inventoryRegistry);

		manager.add(user);

		OfflinePlayer differentHandleSameUuid = mock(OfflinePlayer.class);
		when(differentHandleSameUuid.getUniqueId()).thenReturn(sharedUuid);

		assertSame(user, manager.getUser(handleUsedAtAdd), "the exact same handle used at add() retrieves the cached user");
		assertNull(manager.getUser(differentHandleSameUuid),
				"Observation #1 (users-levels-economy-bank.md) proxy: UserManager's map keys on Bukkit-handle "
				+ "identity, not on the wrapped UUID. Real CraftPlayer/CraftOfflinePlayer instances differ in "
				+ "whether equals()/hashCode() compare the UUID at all, which is what makes the join/quit "
				+ "eviction miss in production; a plain mock cannot reproduce that distinction, but this pins the "
				+ "underlying identity-keyed HashMap mechanism responsible for it.");
	}

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("Observation #12 (users-levels-economy-bank.md): both UserManager beans call initialize() and register a "
	             + "data supplier on the SAME shared repositories - whichever bean initializes last wins, and the "
	             + "other bean's cached users become invisible to repositoryRegistry.saveAll()")
	void bothUserManagerBeans_collideOnTheSharedRepository_lastInitializeWins() {
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
		Supplier<Collection<User<? extends OfflinePlayer>>> lastRegisteredSupplier =
				registeredSuppliers.get(registeredSuppliers.size() - 1);
		Collection<User<? extends OfflinePlayer>> visibleToSaveAll = lastRegisteredSupplier.get();

		assertEquals(1, visibleToSaveAll.size());
		assertTrue(visibleToSaveAll.contains(offlineUser), "the LAST initialize() call wins the shared repository's supplier");
		assertFalse(visibleToSaveAll.contains(onlineUser),
				"the online manager's cached user is invisible to repositoryRegistry.saveAll() once the offline "
				+ "manager also calls initialize() on the same shared repository");
	}

}
