package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.data.account.gang.member.Member;
import me.luckyraven.data.account.gang.member.MemberManager;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.tables.player.BankTable;
import me.luckyraven.database.tables.player.MemberTable;
import me.luckyraven.database.tables.player.UserTable;
import me.luckyraven.events.user.UserDataInitEvent;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.updater.UpdateChecker;
import me.luckyraven.util.ChatUtil;
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
public final class CreateAccount implements Listener {

	private final Gangland                   gangland;
	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final MemberManager              memberManager;
	private final GanglandDatabase           ganglandDatabase;

	public CreateAccount(Gangland gangland) {
		this.gangland           = gangland;
		this.userManager        = gangland.getInitializer().getUserManager();
		this.offlineUserManager = gangland.getInitializer().getOfflineUserManager();
		this.memberManager      = gangland.getInitializer().getMemberManager();
		this.ganglandDatabase   = gangland.getInitializer().getGanglandDatabase();
	}

	// Need to create the account before any other event
	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player       player = event.getPlayer();
		User<Player> user   = new User<>(gangland, player);

		UpdateChecker updateChecker = gangland.getUpdateChecker();

		if (player.hasPermission(updateChecker.getCheckPermission()) && updateChecker.updateAvailable()) {
			player.sendMessage(ChatUtil.prefixMessage(updateChecker.getUpdateMessage()));
		}

		user.getEconomy().setBalance(Settings.getUserInitialBalance());

		// remove the player from the offline user manager
		User<OfflinePlayer> offlineUser = offlineUserManager.getUser(player);

		if (offlineUser != null) {
			offlineUserManager.remove(offlineUser);
		}

		// Add the user to the manager immediately so other handlers can find them
		userManager.add(user);

		Bukkit.getScheduler().runTaskAsynchronously(gangland, () -> {
			List<Table<?>> tables    = ganglandDatabase.getTables();
			UserTable      userTable = gangland.getInitializer().getInstanceFromTables(UserTable.class, tables);
			BankTable      bankTable = gangland.getInitializer().getInstanceFromTables(BankTable.class, tables);

			userManager.initializeUserData(user, userTable, bankTable);

			// Bukkit events must be fired on the main thread
			UserDataInitEvent userDataInitEvent = new UserDataInitEvent(true, user);
			Bukkit.getPluginManager().callEvent(userDataInitEvent);
		});

		// need to check if the user already registered
		Member member = memberManager.getMember(player.getUniqueId());

		if (member != null) {
			userManager.initializeUserPermission(user, member);
			return;
		}

		// if the member is new
		Member newMember = new Member(player.getUniqueId());

		Bukkit.getScheduler().runTaskAsynchronously(gangland, () -> {
			List<Table<?>> tables      = ganglandDatabase.getTables();
			MemberTable    memberTable = gangland.getInitializer().getInstanceFromTables(MemberTable.class, tables);

			memberManager.initializeMemberData(newMember, memberTable);
		});

		memberManager.add(newMember);
	}

}
