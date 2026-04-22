package me.luckyraven.bootstrap;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.core.bean.BeanPostInitialize;
import me.luckyraven.data.user.UserDataLoader;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.TableLookup;
import me.luckyraven.database.tables.player.BankTable;
import me.luckyraven.database.tables.player.MemberTable;
import me.luckyraven.database.tables.player.UserTable;
import me.luckyraven.events.user.UserDataInitEvent;
import me.luckyraven.gang.member.Member;
import me.luckyraven.gang.member.MemberManager;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.item.unique.UniqueItemUtil;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.database.DatabaseHelper;
import me.luckyraven.persistence.database.component.Table;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Populates the user managers with online and offline players from the database. Runs as a {@link BeanPostInitialize}
 * bean so it participates in both first-load and reload cycles <b>after</b> every
 * {@code BeanLifecycle.onInitialize(...)} call has completed — this is critical on reload because loading online
 * players fires {@code WantedStartEvent}, which drives {@code CopManager.startSpawnTask}, which reads a lifecycle-wired
 * {@code CopConfigProvider}. Running in post-init guarantees every lifecycle bean is fully re-wired before any events
 * fire from here.
 *
 * <p>Depends on {@link FileManager} (unused at runtime) solely to force topological ordering: files must be reloaded
 * before players can be loaded, because the addon data (unique items, etc.) is read during user initialization.
 */
@CustomLog
public final class PlayerBootstrapService implements BeanPostInitialize {

	private final Gangland                   gangland;
	private final GanglandDatabase           ganglandDatabase;
	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final MemberManager              memberManager;
	private final UserDataLoader             userDataLoader;
	private final UniqueItemAddon            uniqueItemAddon;

	public PlayerBootstrapService(Gangland gangland,
	                              GanglandDatabase ganglandDatabase,
	                              UserManager<Player> userManager,
	                              UserManager<OfflinePlayer> offlineUserManager,
	                              MemberManager memberManager,
	                              UserDataLoader userDataLoader,
	                              UniqueItemAddon uniqueItemAddon) {
		this.gangland           = gangland;
		this.ganglandDatabase   = ganglandDatabase;
		this.userManager        = userManager;
		this.offlineUserManager = offlineUserManager;
		this.memberManager      = memberManager;
		this.userDataLoader     = userDataLoader;
		this.uniqueItemAddon    = uniqueItemAddon;
	}

	/**
	 * Loads online and offline players into the user managers. Runs on both first load and reload — after a reload the
	 * managers' in-memory state has been wiped by {@link UserManager#onClear()}, so users need to be re-populated from
	 * the database.
	 */
	@Override
	public void onPostInitialize(boolean firstLoad) {
		List<Table<?>> tables      = ganglandDatabase.getTables();
		UserTable      userTable   = TableLookup.find(UserTable.class, tables);
		BankTable      bankTable   = TableLookup.find(BankTable.class, tables);
		MemberTable    memberTable = TableLookup.find(MemberTable.class, tables);

		loadOnlinePlayers(userTable, bankTable, memberTable);
		loadOfflinePlayers(userTable, bankTable);

		log.debug("Player bootstrap complete: {} online, {} offline",
		          userManager.getUsers().size(), offlineUserManager.getUsers().size());
	}

	private void loadOnlinePlayers(UserTable userTable, BankTable bankTable, MemberTable memberTable) {
		var uniqueItems = uniqueItemAddon.getUniqueItems();

		for (Player player : Bukkit.getOnlinePlayers()) {
			User<Player> existingUser = userManager.getUser(player);
			if (existingUser != null) {
				continue;
			}

			var newUser = userManager.create(player);

			// add join-time unique items
			for (var uniqueItem : uniqueItems.values()) {
				if (!uniqueItem.isAddOnJoin()) {
					continue;
				}
				if (!uniqueItem.isAddToInventory()) {
					continue;
				}
				if (UniqueItemUtil.hasUniqueItem(player, uniqueItem) && !uniqueItem.isAllowDuplicates()) {
					continue;
				}
				uniqueItem.addItemToInventory(player);
			}

			userDataLoader.loadUserData(newUser, userTable, bankTable);

			UserDataInitEvent userDataInitEvent = new UserDataInitEvent(false, newUser);
			Bukkit.getPluginManager().callEvent(userDataInitEvent);

			userManager.add(newUser);

			// initialize member data and rank permissions
			Member member = memberManager.getMember(player.getUniqueId());

			if (member != null) {
				userManager.initializeUserPermission(newUser, member);
				continue;
			}

			Member newMember = new Member(player.getUniqueId());
			memberManager.initializeMemberData(newMember, memberTable);
			memberManager.add(newMember);
		}
	}

	private void loadOfflinePlayers(UserTable userTable, BankTable bankTable) {
		DatabaseHelper helper = new DatabaseHelper(gangland, ganglandDatabase);

		helper.runQueries(database -> {
			List<Object[]> allUsers = userTable.selectAllTableQuery(database);

			for (Object[] userData : allUsers) {
				String uuidString = String.valueOf(userData[0]);
				UUID   uuid       = UUID.fromString(uuidString);

				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
				if (offlinePlayer.isOnline()) {
					continue;
				}

				User<OfflinePlayer> existingUser = offlineUserManager.getUser(offlinePlayer);
				if (existingUser != null) {
					continue;
				}

				User<OfflinePlayer> offlineUser = offlineUserManager.create(offlinePlayer);
				userDataLoader.loadUserData(offlineUser, userTable, bankTable);
				offlineUserManager.add(offlineUser);
			}
		});
	}
}
