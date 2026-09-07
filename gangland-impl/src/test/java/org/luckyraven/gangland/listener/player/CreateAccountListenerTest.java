package org.luckyraven.gangland.listener.player;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.data.user.UserDataLoader;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.database.tables.player.BankTable;
import org.luckyraven.gangland.database.tables.player.UserTable;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.math.BigDecimal;
import java.nio.file.Path;
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
 * Pins the join-time economy contract of {@link CreateAccountListener}.
 *
 * <p>Observation #2 (users-levels-economy-bank.md) / US-02: the handler used to call
 * {@code user.getEconomy().setAmount(Settings.getUserInitialBalance())} unconditionally on <em>every</em> join,
 * before the async database read. {@code EconomyHandler.setAmount} withdraws the entire Vault balance and
 * re-deposits the new figure, so with {@code Initial_Balance: 0} (the shipped default) a returning player was
 * zeroed for the whole round-trip — permanently when the row was missing or the query failed. The starting
 * balance now belongs to {@code UserDataLoader}, which applies it only when the database confirms there is no
 * saved row.
 *
 * <p>Observation #1 / US-01 is pinned here too: the join must evict the quit-time offline snapshot by uuid, not
 * by a Bukkit handle that can never match the one the quit path stored.
 */
@DisplayName("CreateAccountListener - join must not touch a returning player's balance")
class CreateAccountListenerTest {

	@TempDir
	Path tempDir;

	private BukkitStatics bukkit;

	private Gangland                   gangland;
	private UserManager<Player>        userManager;
	private UserManager<OfflinePlayer> offlineUserManager;
	private MemberManager              memberManager;
	private UserDataLoader             userDataLoader;
	private GanglandDatabase           ganglandDatabase;

	private CreateAccountListener listener;

	private Player       player;
	private UUID         uuid;
	private User<Player> user;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		SettingsFixture.initializeMinimal(tempDir);

		bukkit = BukkitStatics.install();

		gangland           = mock(Gangland.class);
		userManager        = mock(UserManager.class);
		offlineUserManager = mock(UserManager.class);
		memberManager      = mock(MemberManager.class);
		userDataLoader     = mock(UserDataLoader.class);
		ganglandDatabase   = mock(GanglandDatabase.class);

		// The updater is optional (CM-01); a null checker is the disabled-updater shape and keeps this test off
		// the notification path entirely.
		when(gangland.getUpdateChecker()).thenReturn(null);

		List<Table<?>> tables = List.of(mock(UserTable.class), mock(BankTable.class));
		when(ganglandDatabase.getTables()).thenReturn(tables);

		uuid   = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);
		// The async continuation bails out here, so the test stops where the data load hands over.
		when(player.isOnline()).thenReturn(false);

		Member member = new Member(uuid);
		member.setGangId(7);   // hasGang() -> true, so the member-table branch is skipped
		when(memberManager.getMember(uuid)).thenReturn(member);

		user = mock(User.class);
		when(userManager.create(player)).thenReturn(user);

		listener = new CreateAccountListener(gangland, userManager, offlineUserManager, memberManager,
		                                     userDataLoader, ganglandDatabase);
	}

	@AfterEach
	void tearDown() {
		if (bukkit != null) bukkit.close();
	}

	/**
	 * A detached handler (no {@code EconomyOwner}, so no Vault round-trip) seeded with a balance. In production the
	 * handler is user-backed and {@code setAmount} rewrites the real Vault account; here any write is still visible
	 * as a changed amount, which is all the assertion needs.
	 */
	private EconomyHandler economyWith(double balance) {
		EconomyHandler economy = new EconomyHandler(Currency.of(balance), null, false);
		when(user.getEconomy()).thenReturn(economy);
		return economy;
	}

	private PlayerJoinEvent joinEvent() {
		PlayerJoinEvent event = mock(PlayerJoinEvent.class);
		when(event.getPlayer()).thenReturn(player);
		return event;
	}

	@Test
	@DisplayName("US-02: a returning player's balance survives the join untouched")
	void onPlayerJoin_doesNotResetTheBalanceToTheInitialBalance() {
		EconomyHandler economy = economyWith(5_000D);

		listener.onPlayerJoin(joinEvent());

		assertEquals(0, Currency.of(5_000D).compareTo(economy.getAmount()),
		             "the join handler must never write to the economy: EconomyHandler.setAmount clears the "
		             + "backing Vault account before re-depositing, so stamping Initial_Balance on every join "
		             + "zeroed a returning player until (and unless) the async read came back");
	}

	@Test
	@DisplayName("US-02: the join handler performs no economy write at all, whatever Initial_Balance is configured")
	void onPlayerJoin_neverWritesToTheEconomy() {
		EconomyHandler economy = mock(EconomyHandler.class);
		when(user.getEconomy()).thenReturn(economy);

		listener.onPlayerJoin(joinEvent());

		verify(economy, never()).setAmount(any(BigDecimal.class));
		verify(economy, never()).depositAmount(any(BigDecimal.class));
		verify(economy, never()).withdrawAmount(any(BigDecimal.class));
	}

	@Test
	@DisplayName("the data loader is still the thing that hydrates the user")
	void onPlayerJoin_stillHandsTheUserToTheDataLoader() {
		economyWith(5_000D);

		listener.onPlayerJoin(joinEvent());

		verify(userDataLoader).loadUserData(eq(user), any(UserTable.class), any(BankTable.class));
		verify(userManager).add(user);
	}

	@Test
	@DisplayName("US-01: the quit-time offline snapshot is evicted by uuid, not by a Bukkit handle")
	void onPlayerJoin_evictsTheOfflineSnapshotByUuid() {
		economyWith(5_000D);

		listener.onPlayerJoin(joinEvent());

		verify(offlineUserManager).remove(uuid);
	}

}
