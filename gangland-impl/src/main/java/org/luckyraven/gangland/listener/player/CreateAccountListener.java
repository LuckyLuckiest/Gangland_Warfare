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
import org.luckyraven.gangland.database.tables.player.UserTable;
import org.luckyraven.gangland.events.user.UserDataInitEvent;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.update.UpdateNotifier;

import java.util.List;

@ListenerHandler(priority = ListenerPriority.LOWEST)
public final class CreateAccountListener implements Listener {

	private final Gangland                   gangland;
	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final UserDataLoader             userDataLoader;
	private final GanglandDatabase           ganglandDatabase;

	public CreateAccountListener(Gangland gangland,
	                             @Qualifier("online") UserManager<Player> userManager,
	                             @Qualifier("offline") UserManager<OfflinePlayer> offlineUserManager,
	                             UserDataLoader userDataLoader,
	                             GanglandDatabase ganglandDatabase) {
		this.gangland           = gangland;
		this.userManager        = userManager;
		this.offlineUserManager = offlineUserManager;
		this.userDataLoader     = userDataLoader;
		this.ganglandDatabase   = ganglandDatabase;
	}

	// Need to create the account before any other event
	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player       player = event.getPlayer();
		User<Player> user   = userManager.create(player);

		notifyUpdate(player, gangland.getUpdateChecker());

		// The starting balance is NOT stamped here: EconomyHandler.setAmount zeroes the real Vault account
		// before re-depositing, so doing it on every join would wipe a returning player's money for the length
		// of the async round-trip (permanently if the query fails). UserDataLoader applies it only when the DB
		// confirms there is no saved row for this player.

		// Remove the player from the offline user manager. Keyed by uuid, so the quit-time snapshot cannot
		// survive the rejoin and overwrite the live row on the next autosave.
		offlineUserManager.remove(player.getUniqueId());

		// Add user to cache immediately so other systems can find them
		userManager.add(user);

		// Load data from DB asynchronously, then fire the init event.
		// initializeUserData updates the same user object in-place, so the cached reference
		// gets the DB values once the async load completes.
		Bukkit.getScheduler().runTaskAsynchronously(gangland, () -> {
			List<Table<?>> tables    = ganglandDatabase.getTables();
			UserTable      userTable = TableLookup.find(UserTable.class, tables);
			BankTable      bankTable = TableLookup.find(BankTable.class, tables);

			userDataLoader.loadUserData(user, userTable, bankTable);

			if (!player.isOnline()) {
				return;
			}

			// UserDataInitEvent is declared async — downstream listeners (LoadUniqueItem, the gang module's
			// MemberJoinListener which creates/caches the player's Member row and applies rank permissions)
			// hop back to the main thread themselves, so fire it here in the async context.
			UserDataInitEvent userDataInitEvent = new UserDataInitEvent(true, user);
			Bukkit.getPluginManager().callEvent(userDataInitEvent);
		});
	}

	/**
	 * Sends the operator update notice, when there is one to send.
	 *
	 * <p>CM-01: {@code Gangland.updateCheckerInitializer()} returns before constructing the notifier whenever
	 * {@code Update_Checker.Enable} is {@code false}, so {@code gangland.getUpdateChecker()} is {@code null} on
	 * every server that disabled the updater. This handler runs at {@link EventPriority#LOWEST}, so dereferencing
	 * it unconditionally aborted the whole join handler before {@code userManager.add(user)} and left the player
	 * with no cached {@link User}. The null guard is the point of this seam.
	 *
	 * @return {@code true} when a notice was actually sent.
	 */
	static boolean notifyUpdate(Player player, UpdateNotifier updateChecker) {
		if (updateChecker == null) {
			return false;
		}

		if (!player.hasPermission(updateChecker.getCheckPermission()) || !updateChecker.updateAvailable()) {
			return false;
		}

		player.sendMessage(GanglandChatUtil.prefixMessage(updateChecker.getUpdateMessage()));
		return true;
	}

}
