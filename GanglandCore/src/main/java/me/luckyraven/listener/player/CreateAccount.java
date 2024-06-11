package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserDataInitEvent;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.component.Table;
import me.luckyraven.database.sub.GanglandDatabase;
import me.luckyraven.database.tables.BankTable;
import me.luckyraven.database.tables.MemberTable;
import me.luckyraven.database.tables.UserTable;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

public final class CreateAccount implements Listener {

	private final Gangland            gangland;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GanglandDatabase    ganglandDatabase;

	public CreateAccount(Gangland gangland) {
		this.gangland         = gangland;
		this.memberManager    = gangland.getInitializer().getMemberManager();
		this.userManager      = gangland.getInitializer().getUserManager();
		this.ganglandDatabase = gangland.getInitializer().getGanglandDatabase();
	}

	// Need to create the account before any other event
	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player       player = event.getPlayer();
		User<Player> user   = new User<>(player);

		user.getEconomy().setBalance(SettingAddon.getUserInitialBalance());

		Bukkit.getScheduler().runTaskAsynchronously(gangland, () -> {
			List<Table<?>> tables    = ganglandDatabase.getTables().stream().toList();
			UserTable      userTable = gangland.getInitializer().getInstanceFromTables(UserTable.class, tables);
			BankTable      bankTable = gangland.getInitializer().getInstanceFromTables(BankTable.class, tables);

			userManager.initializeUserData(user, userTable, bankTable);

			UserDataInitEvent userDataInitEvent = new UserDataInitEvent(true, user);
			Bukkit.getPluginManager().callEvent(userDataInitEvent);
		});

		// Add the user to a user manager group
		userManager.add(user);

		// need to check if the user already registered
		Member member = memberManager.getMember(player.getUniqueId());

		if (member != null) userManager.initializeUserPermission(user, member);
		else {
			// if the member is new
			Member newMember = new Member(player.getUniqueId());

			Bukkit.getScheduler().runTaskAsynchronously(gangland, () -> {
				List<Table<?>> tables      = ganglandDatabase.getTables().stream().toList();
				MemberTable    memberTable = gangland.getInitializer().getInstanceFromTables(MemberTable.class, tables);

				memberManager.initializeMemberData(newMember, memberTable);
			});

			memberManager.add(newMember);
		}
	}

}
