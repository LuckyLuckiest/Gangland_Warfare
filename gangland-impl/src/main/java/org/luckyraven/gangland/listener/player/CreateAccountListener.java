package org.luckyraven.gangland.listener.player;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.bean.listener.ListenerPriority;
import org.luckyraven.gangland.data.user.UserDataLoader;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.database.TableLookup;
import org.luckyraven.gangland.database.tables.player.BankTable;
import org.luckyraven.gangland.database.tables.player.MemberTable;
import org.luckyraven.gangland.database.tables.player.UserTable;
import org.luckyraven.gangland.events.user.UserDataInitEvent;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.gangland.util.UpdateNotifier;

import java.util.List;

@ListenerHandler(priority = ListenerPriority.LOWEST)
public final class CreateAccountListener implements Listener {

	private final Gangland                   gangland;
	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final MemberManager              memberManager;
	private final UserDataLoader             userDataLoader;
	private final GanglandDatabase           ganglandDatabase;

	public CreateAccountListener(Gangland gangland,
	                             @Qualifier("online") UserManager<Player> userManager,
	                             @Qualifier("offline") UserManager<OfflinePlayer> offlineUserManager,
	                             MemberManager memberManager,
	                             UserDataLoader userDataLoader,
	                             GanglandDatabase ganglandDatabase) {
		this.gangland           = gangland;
		this.userManager        = userManager;
		this.offlineUserManager = offlineUserManager;
		this.memberManager      = memberManager;
		this.userDataLoader     = userDataLoader;
		this.ganglandDatabase   = ganglandDatabase;
	}

	// Need to create the account before any other event
	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player       player = event.getPlayer();
		User<Player> user   = userManager.create(player);

		UpdateNotifier updateChecker = gangland.getUpdateChecker();

		if (player.hasPermission(updateChecker.getCheckPermission()) && updateChecker.updateAvailable()) {
			player.sendMessage(GanglandChatUtil.prefixMessage(updateChecker.getUpdateMessage()));
		}

		user.getEconomy().setAmount(Settings.getUserInitialBalance());

		// Remove the player from the offline user manager
		User<OfflinePlayer> offlineUser = offlineUserManager.getUser(player);

		if (offlineUser != null) {
			offlineUserManager.remove(offlineUser);
		}

		// Add user and member to cache immediately so other systems can find them
		userManager.add(user);

		Member member = memberManager.getMember(player.getUniqueId());

		if (member == null) {
			member = new Member(player.getUniqueId());
			memberManager.add(member);
		}

		Member finalMember = member;

		// Load data from DB asynchronously, then fire the init event on the main thread.
		// initializeUserData updates the same user object in-place, so the cached reference
		// gets the DB values once the async load completes.
		Bukkit.getScheduler().runTaskAsynchronously(gangland, () -> {
			List<Table<?>> tables    = ganglandDatabase.getTables();
			UserTable      userTable = TableLookup.find(UserTable.class, tables);
			BankTable      bankTable = TableLookup.find(BankTable.class, tables);

			userDataLoader.loadUserData(user, userTable, bankTable);

			if (!finalMember.hasGang()) {
				MemberTable memberTable = TableLookup.find(MemberTable.class, tables);
				memberManager.initializeMemberData(finalMember, memberTable);
			}

			if (!player.isOnline()) {
				return;
			}

			// UserDataInitEvent is declared async (downstream listeners like LoadUniqueItem
			// hop back to main thread themselves), so fire it here in the async context.
			UserDataInitEvent userDataInitEvent = new UserDataInitEvent(true, user);
			Bukkit.getPluginManager().callEvent(userDataInitEvent);

			// PermissionAttachment / player.updateCommands() inside initializeUserPermission
			// must run on the main thread.
			Bukkit.getScheduler().runTask(gangland, () -> {
				if (!player.isOnline()) {
					return;
				}

				userManager.initializeUserPermission(user, finalMember);
			});
		});
	}

}
