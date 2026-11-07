package org.luckyraven.gangland.core.user;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.keystone.economy.bank.Bank;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.luckyraven.keystone.util.Placeholder;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the bank data supplier {@link UserManager#initialize()} registers: it yields the bank of every cached,
 * load-complete user that has one, across the whole linked group, in the same order the user supplier walks them.
 */
@DisplayName("UserManager - the bank data supplier")
class UserManagerGroupedBanksTest {

	private final JavaPlugin  plugin      = mock(JavaPlugin.class);
	private final Placeholder placeholder = mock(Placeholder.class);

	private Supplier<Collection<User<? extends OfflinePlayer>>> userSupplier;
	private Supplier<Collection<Bank>>                          bankSupplier;

	@BeforeEach
	void bindSettings() {
		IdentitySettings.bind(new FakeIdentitySettingsContract());
	}

	@SuppressWarnings("unchecked")
	private void initialize(UserManager<?>... managers) {
		RepositoryRegistry                         registry = mock(RepositoryRegistry.class);
		IRepository<User<? extends OfflinePlayer>> userRepo = mock(IRepository.class);
		IRepository<Bank>                          bankRepo = mock(IRepository.class);
		when(registry.<User<? extends OfflinePlayer>>getGenericRepository(User.class)).thenReturn(userRepo);
		when(registry.getRepository(Bank.class)).thenReturn(bankRepo);

		UserManager<OfflinePlayer> manager = new UserManager<>(plugin, registry, mock(UserFactory.class));
		for (UserManager<?> other : managers) {
			manager.link(other);
		}
		linked = manager;
		manager.initialize();

		ArgumentCaptor<Supplier<Collection<User<? extends OfflinePlayer>>>> users = ArgumentCaptor.forClass(Supplier.class);
		ArgumentCaptor<Supplier<Collection<Bank>>>                          banks = ArgumentCaptor.forClass(Supplier.class);
		verify(userRepo).setDataSupplier(users.capture());
		verify(bankRepo).setDataSupplier(banks.capture());
		userSupplier = users.getValue();
		bankSupplier = banks.getValue();
	}

	private UserManager<OfflinePlayer> linked;

	private UserManager<Player> onlineManager() {
		return new UserManager<>(plugin, mock(RepositoryRegistry.class), mock(UserFactory.class));
	}

	private <T extends OfflinePlayer> User<T> user(T handle, Bank bank, boolean pending) {
		when(handle.getUniqueId()).thenReturn(UUID.randomUUID());
		User<T> user = new User<>(plugin, handle, placeholder);
		user.setBank(bank);
		user.setLoadPending(pending);
		return user;
	}

	@Test
	@DisplayName("only banks of load-complete users that have one, none for a pending user or a user without a bank")
	void banks_skipPendingUsersAndUsersWithoutABank() {
		initialize();
		Bank kept = mock(Bank.class);
		linked.add(user(mock(OfflinePlayer.class), kept, false));
		linked.add(user(mock(OfflinePlayer.class), mock(Bank.class), true));
		linked.add(user(mock(OfflinePlayer.class), null, false));

		assertEquals(List.of(kept), List.copyOf(bankSupplier.get()));
	}

	@Test
	@DisplayName("a pending user with no bank is skipped as well")
	void banks_pendingUserWithoutABank_isSkipped() {
		initialize();
		linked.add(user(mock(OfflinePlayer.class), null, true));

		assertTrue(bankSupplier.get().isEmpty());
	}

	@Test
	@DisplayName("no cached users gives an empty list")
	void banks_noUsers_isEmpty() {
		initialize();

		assertTrue(bankSupplier.get().isEmpty());
	}

	@Test
	@DisplayName("the union of the linked group, in the order the user supplier walks the users")
	void banks_linkedGroup_followTheUserSupplierOrder() {
		UserManager<Player> online = onlineManager();
		initialize(online);
		for (int i = 0; i < 6; i++) {
			linked.add(user(mock(OfflinePlayer.class), mock(Bank.class), false));
			online.add(user(mock(Player.class), mock(Bank.class), false));
		}
		linked.add(user(mock(OfflinePlayer.class), null, false));
		online.add(user(mock(Player.class), mock(Bank.class), true));

		List<Bank> expected = userSupplier.get().stream().filter(User::hasBank).map(User::getBank).toList();

		assertEquals(12, expected.size());
		assertEquals(expected, List.copyOf(bankSupplier.get()));
	}

	@Test
	@DisplayName("every call builds a fresh mutable list that reflects the current caches")
	void banks_everyCallIsAFreshMutableList() {
		initialize();
		Bank first = mock(Bank.class);
		linked.add(user(mock(OfflinePlayer.class), first, false));

		Collection<Bank> one = bankSupplier.get();
		one.add(mock(Bank.class));
		Collection<Bank> two = bankSupplier.get();

		assertNotSame(one, two);
		assertEquals(List.of(first), List.copyOf(two));
	}
}
