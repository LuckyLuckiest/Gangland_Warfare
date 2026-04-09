package me.luckyraven.bootstrap;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.data.account.gang.member.Member;
import me.luckyraven.data.account.gang.member.MemberManager;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.TableLookup;
import me.luckyraven.database.tables.player.BankTable;
import me.luckyraven.database.tables.player.MemberTable;
import me.luckyraven.database.tables.player.UserTable;
import me.luckyraven.events.user.UserDataInitEvent;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.item.unique.UniqueItemUtil;
import me.luckyraven.persistence.database.DatabaseHelper;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.util.autowire.bean.BeanLifecycle;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Populates the user managers with online and offline players from the database. Runs as a {@link BeanLifecycle} bean
 * so it participates in both first-load and reload cycles, replacing the manual
 * {@code ReloadPlugin.loadOnlinePlayers()} call from {@code Gangland.onEnable()}.
 *
 * <p>Depends on {@link FileAddonReloadService} (unused at runtime) solely to force topological ordering: files must be
 * reloaded before players can be loaded, because the addon data (unique items, etc.) is read during user
 * initialization.
 */
@CustomLog
public final class PlayerBootstrapService implements BeanLifecycle {

	private final Gangland                   gangland;
	private final GanglandDatabase           ganglandDatabase;
	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final MemberManager              memberManager;
	private final UniqueItemAddon            uniqueItemAddon;

	@SuppressWarnings("unused") // forces topo ordering — files reload before players load
	private final FileAddonReloadService fileAddonReloadService;

	public PlayerBootstrapService(Gangland gangland,
	                              GanglandDatabase ganglandDatabase,
	                              UserManager<Player> userManager,
	                              UserManager<OfflinePlayer> offlineUserManager,
	                              MemberManager memberManager,
	                              UniqueItemAddon uniqueItemAddon,
	                              FileAddonReloadService fileAddonReloadService) {
		this.gangland               = gangland;
		this.ganglandDatabase       = ganglandDatabase;
		this.userManager            = userManager;
		this.offlineUserManager     = offlineUserManager;
		this.memberManager          = memberManager;
		this.uniqueItemAddon        = uniqueItemAddon;
		this.fileAddonReloadService = fileAddonReloadService;
	}

	/**
	 * Loads online and offline players into the user managers. Runs on both first load and reload — after a reload the
	 * managers' in-memory state has been wiped by {@link UserManager#onClear()}, so users need to be re-populated from
	 * the database.
	 */
	@Override
	public void onInitialize(boolean firstLoad) {
		List<Table<?>> tables      = ganglandDatabase.getTables();
		UserTable      userTable   = TableLookup.find(UserTable.class, tables);
		BankTable      bankTable   = TableLookup.find(BankTable.class, tables);
		MemberTable    memberTable = TableLookup.find(MemberTable.class, tables);

		loadOnlinePlayers(userTable, bankTable, memberTable);
		loadOfflinePlayers(userTable, bankTable);

		log.info("Player bootstrap complete: {} online, {} offline",
		         userManager.getUsers().size(), offlineUserManager.getUsers().size());
	}

	private void loadOnlinePlayers(UserTable userTable, BankTable bankTable, MemberTable memberTable) {
		var uniqueItems = uniqueItemAddon.getUniqueItems();

		for (Player player : Bukkit.getOnlinePlayers()) {
			User<Player> existingUser = userManager.getUser(player);
			if (existingUser != null) {
				continue;
			}

			var newUser = new User<>(gangland, player);

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

			userManager.initializeUserData(newUser, userTable, bankTable);

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

				User<OfflinePlayer> offlineUser = new User<>(gangland, offlinePlayer);
				offlineUserManager.initializeUserData(offlineUser, userTable, bankTable);
				offlineUserManager.add(offlineUser);
			}
		});
	}
}
