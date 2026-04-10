package me.luckyraven.listener.player;

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
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.UpdateChecker;
import me.luckyraven.util.autowire.bean.Qualifier;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.listener.ListenerPriority;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

@ListenerHandler(priority = ListenerPriority.LOWEST)
public final class CreateAccountListener implements Listener {

	private final Gangland                   gangland;
	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final MemberManager              memberManager;
	private final GanglandDatabase           ganglandDatabase;

	public CreateAccountListener(Gangland gangland,
	                             @Qualifier("online") UserManager<Player> userManager,
	                             @Qualifier("offline") UserManager<OfflinePlayer> offlineUserManager,
	                             MemberManager memberManager,
	                             GanglandDatabase ganglandDatabase) {
		this.gangland           = gangland;
		this.userManager        = userManager;
		this.offlineUserManager = offlineUserManager;
		this.memberManager      = memberManager;
		this.ganglandDatabase   = ganglandDatabase;
	}

	// Need to create the account before any other event
	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player       player = event.getPlayer();
		User<Player> user   = new User<>(gangland, player);

		UpdateChecker updateChecker = gangland.getUpdateChecker();

		if (player.hasPermission(updateChecker.getCheckPermission()) && updateChecker.updateAvailable()) {
			player.sendMessage(GanglandChatUtil.prefixMessage(updateChecker.getUpdateMessage()));
		}

		user.getEconomy().setBalance(Settings.getUserInitialBalance());

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

			userManager.initializeUserData(user, userTable, bankTable);

			if (!finalMember.hasGang()) {
				MemberTable memberTable = TableLookup.find(MemberTable.class, tables);
				memberManager.initializeMemberData(finalMember, memberTable);
			}

			if (!player.isOnline()) {
				return;
			}

			UserDataInitEvent userDataInitEvent = new UserDataInitEvent(true, user);
			Bukkit.getPluginManager().callEvent(userDataInitEvent);

			userManager.initializeUserPermission(user, finalMember);
		});
	}

}
