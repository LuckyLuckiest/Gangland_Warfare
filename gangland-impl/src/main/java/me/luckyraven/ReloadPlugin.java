package me.luckyraven;

import lombok.CustomLog;
import me.luckyraven.context.GanglandContext;
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
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.item.unique.UniqueItemUtil;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.database.DatabaseHelper;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.scoreboard.Scoreboard;
import me.luckyraven.scoreboard.ScoreboardManager;
import me.luckyraven.scoreboard.driver.DriverHandler;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@CustomLog
public final class ReloadPlugin {

	private final Gangland        gangland;
	private final Initializer     initializer;
	private final GanglandContext context;

	public ReloadPlugin(Gangland gangland) {
		this.gangland    = gangland;
		this.initializer = gangland.getInitializer();
		this.context     = initializer.getContext();
	}

	/**
	 * Reloads the whole plugin files, scoreboards, and database.
	 *
	 * @param resetCache if old data needs to be cleared
	 */
	public void reload(boolean resetCache) {
		filesReload();
		// when resetting the cache, there would be a problem with the scoreboard trying to get some values
		// first killing all scoreboards, then initializing the data
		killAllScoreboards();
		if (resetCache) {
			context.reloadBeans();
			// re-populate online and offline users — reloadBeans() clears them via onClear()
			// but onInitialize(false) only sets data suppliers, not the actual user objects
			loadOnlinePlayers();
		}
		if (Settings.isScoreboardEnabled()) scoreboardReload();
		periodicalUpdatesReload();
	}

	/**
	 * Reloads the files and their linked addons.
	 */
	public void filesReload() {
		// first, remove all the data saved from the files
		initializer.addonsClear();
		// second, reload the files
		initializer.getFileManager().reloadFiles();
		initializer.addonsLoader();
		// third, update message addon with new language configuration
		Messages.setMessageConfiguration(initializer.getLanguageLoader().getMessage());
	}

	/**
	 * Reloads the scoreboard content from the file.
	 */
	public void scoreboardReload() {
		// reload scoreboard file
		FileManager fileManager = initializer.getFileManager();
		try {
			fileManager.checkFileLoaded("scoreboard");

			FileHandler scoreboard = fileManager.getFile("scoreboard");
			if (scoreboard == null) throw new IOException("scoreboard file is not loaded!");
			scoreboard.reloadData();
		} catch (IOException exception) {
			log.error("scoreboard file is not loaded!", exception);
			return;
		}

		initializer.scoreboardLoader();

		for (User<Player> user : initializer.getUserManager().getUsers().values()) {
			killScoreboard(user);

			ScoreboardManager scoreboardManager = initializer.getScoreboardManager();
			DriverHandler     driverHandler     = scoreboardManager.getDriverHandler(user.getUser());
			Scoreboard        scoreboard        = new Scoreboard(gangland, driverHandler);

			user.setScoreboard(scoreboard);
			user.getScoreboard().start();
		}
	}

	/**
	 * Reloads the periodical updates.
	 */
	public void periodicalUpdatesReload() {
		gangland.getPeriodicalUpdates().stop();
		gangland.periodicalUpdatesInitializer();
	}

	/**
	 * Reloads the inventory information.
	 */
	public void inventoryReload() {
		// Simple inventory reload for special inventories ONLY
		InventoryHandler.removeAllSpecialInventories();
		initializer.inventoryLoader();
	}

	/**
	 * Loads online and offline players into the user managers. Called once during first enable to pick up any players
	 * who joined before the plugin finished initializing. Not part of the bean lifecycle — this is a one-time bootstrap
	 * step.
	 */
	public void loadOnlinePlayers() {
		GanglandDatabase           ganglandDatabase = initializer.getGanglandDatabase();
		UserManager<Player>        userManager      = initializer.getUserManager();
		MemberManager              memberManager    = initializer.getMemberManager();
		UserManager<OfflinePlayer> offlineManager   = initializer.getOfflineUserManager();

		List<Table<?>>  tables          = ganglandDatabase.getTables();
		UserTable       userTable       = TableLookup.find(UserTable.class, tables);
		BankTable       bankTable       = TableLookup.find(BankTable.class, tables);
		MemberTable     memberTable     = TableLookup.find(MemberTable.class, tables);
		UniqueItemAddon uniqueItemAddon = initializer.getUniqueItemAddon();

		// get the online users
		for (Player player : Bukkit.getOnlinePlayers()) {
			User<Player> onlineUser = userManager.getUser(player);

			if (onlineUser != null) continue;

			var newUser = new User<>(gangland, player);

			// add all the unique items
			var uniqueItems = uniqueItemAddon.getUniqueItems();

			for (var uniqueItem : uniqueItems.values()) {
				if (!uniqueItem.isAddOnJoin()) continue;
				if (!uniqueItem.isAddToInventory()) continue;

				if (UniqueItemUtil.hasUniqueItem(player, uniqueItem) && !uniqueItem.isAllowDuplicates()) continue;

				uniqueItem.addItemToInventory(player);
			}

			userManager.initializeUserData(newUser, userTable, bankTable);

			UserDataInitEvent userDataInitEvent = new UserDataInitEvent(false, newUser);
			Bukkit.getPluginManager().callEvent(userDataInitEvent);

			userManager.add(newUser);

			// this member doesn't have a gang because they are new
			Member member = memberManager.getMember(player.getUniqueId());

			// initialize the rank permissions
			if (member != null) {
				userManager.initializeUserPermission(newUser, member);
				continue;
			}

			// for a new member
			Member newMember = new Member(player.getUniqueId());

			memberManager.initializeMemberData(newMember, memberTable);

			memberManager.add(newMember);
		}

		// get the offline users
		DatabaseHelper helper = new DatabaseHelper(gangland, ganglandDatabase);

		helper.runQueries(database -> {
			// select all users from the user table
			List<Object[]> allUsers = userTable.selectAllTableQuery(database);

			for (Object[] userData : allUsers) {
				String uuidString = String.valueOf(userData[0]);
				UUID   uuid       = UUID.fromString(uuidString);

				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
				if (offlinePlayer.isOnline()) continue;

				User<OfflinePlayer> existingUser = offlineManager.getUser(offlinePlayer);
				if (existingUser != null) continue;

				User<OfflinePlayer> offlineUser = new User<>(gangland, offlinePlayer);

				offlineManager.initializeUserData(offlineUser, userTable, bankTable);

				offlineManager.add(offlineUser);
			}
		});
	}

	private void killAllScoreboards() {
		for (User<Player> user : initializer.getUserManager().getUsers().values())
			killScoreboard(user);
	}

	private void killScoreboard(User<Player> user) {
		if (user.getScoreboard() == null) return;

		user.getScoreboard().end();
		user.setScoreboard(null);
	}

}
