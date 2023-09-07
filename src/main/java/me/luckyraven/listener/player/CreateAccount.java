package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserDataInitEvent;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class CreateAccount implements Listener {

	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final Gangland            gangland;

	public CreateAccount(Gangland gangland) {
		this.gangland = gangland;
		this.memberManager = gangland.getInitializer().getMemberManager();
		this.userManager = gangland.getInitializer().getUserManager();
	}

	// Need to create the account before any other event
	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player       player = event.getPlayer();
		User<Player> user   = new User<>(player);

		user.getEconomy().setBalance(SettingAddon.getUserInitialBalance());

		Bukkit.getScheduler().runTaskAsynchronously(gangland, () -> {
			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
				if (handler instanceof UserDatabase userDatabase) {
					userManager.initializeUserData(user, userDatabase);

					UserDataInitEvent userDataInitEvent = new UserDataInitEvent(true, user);
					Bukkit.getPluginManager().callEvent(userDataInitEvent);
					break;
				}
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
				for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
					if (handler instanceof GangDatabase gangDatabase) {
						memberManager.initializeMemberData(newMember, gangDatabase);
						break;
					}
			});

			memberManager.add(newMember);
		}
	}

}
