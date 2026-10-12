package org.luckyraven.gangland.bootstrap;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.user.UserDataLoader;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.database.tables.player.BankTable;
import org.luckyraven.gangland.database.tables.player.UserTable;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.persistence.database.Database;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the boot-time load contract of {@link PlayerBootstrapService} (T-286): a failed user read must never reach the
 * managers, because the cached defaults would then be written back over the stored row by the next autosave.
 */
@DisplayName("PlayerBootstrapService - a failed user read must not cache the user")
class PlayerBootstrapServiceTest {

	@TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.NEVER)
	Path tempDir;

	private BukkitStatics bukkit;

	private UserManager<Player>        userManager;
	private UserManager<OfflinePlayer> offlineUserManager;
	private UserDataLoader             userDataLoader;
	private UserTable                  userTable;
	private BankTable                  bankTable;
	private GanglandDatabase           ganglandDatabase;
	private UniqueItemAddon            uniqueItemAddon;

	private PlayerBootstrapService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		SettingsFixture.initializeMinimal(tempDir);

		bukkit = BukkitStatics.install();
		Messages.init(new FakeMessageProvider().withString("Errors.User.Data_Load_Failed", "reload please"));

		userManager        = mock(UserManager.class);
		offlineUserManager = mock(UserManager.class);
		userDataLoader     = mock(UserDataLoader.class);
		userTable          = mock(UserTable.class);
		bankTable          = mock(BankTable.class);
		ganglandDatabase   = mock(GanglandDatabase.class);
		uniqueItemAddon    = mock(UniqueItemAddon.class);

		when(ganglandDatabase.getDatabase()).thenReturn(mock(Database.class));
		List<Table<?>> tables = List.of(userTable, bankTable);
		when(ganglandDatabase.getTables()).thenReturn(tables);

		service = new PlayerBootstrapService(mock(Gangland.class), ganglandDatabase, userManager,
		                                     offlineUserManager, userDataLoader, uniqueItemAddon);
	}

	@AfterEach
	void tearDown() {
		if (bukkit != null) bukkit.close();
	}

	@Test
	@DisplayName("T-286: an online player whose read fails is neither cached nor announced")
	void onPostInitialize_failedOnlineLoad_cachesNothingAndFiresNoEvent() {
		UUID        uuid   = UUID.randomUUID();
		Player      player = mock(Player.class);
		User<Player> user  = mock(User.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(userManager.create(player)).thenReturn(user);
		when(userDataLoader.loadUserData(any(), any(), any())).thenReturn(false);
		bukkit.statics().when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
		var pluginManager = bukkit.pluginManager();

		service.onPostInitialize(true);

		verify(userManager, never()).add(any());
		verify(pluginManager, never()).callEvent(any(Event.class));
	}

	@Test
	@DisplayName("T-286: an offline player whose read fails is not added to the offline manager")
	void onPostInitialize_failedOfflineLoad_cachesNothing() throws Exception {
		UUID                 uuid           = UUID.randomUUID();
		OfflinePlayer        offlinePlayer  = mock(OfflinePlayer.class);
		User<OfflinePlayer>  offlineUser    = mock(User.class);
		when(offlinePlayer.isOnline()).thenReturn(false);
		when(offlineUserManager.create(offlinePlayer)).thenReturn(offlineUser);
		when(userTable.selectAllTableQuery(any())).thenReturn(List.<Object[]>of(new Object[]{uuid.toString()}));
		when(userDataLoader.loadUserData(any(), any(), any())).thenReturn(false);
		bukkit.statics().when(() -> Bukkit.getOfflinePlayer(uuid)).thenReturn(offlinePlayer);

		service.onPostInitialize(true);

		verify(userDataLoader).loadUserData(eq(offlineUser), any(), any());
		verify(offlineUserManager, never()).add(any());
	}

}
