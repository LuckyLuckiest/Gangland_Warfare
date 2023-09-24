package me.luckyraven;

import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.database.sub.RankDatabase;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.database.sub.WaypointDatabase;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.timer.RepeatingTimer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class PeriodicalUpdates {

	private final Gangland       gangland;
	private       RepeatingTimer repeatingTimer;

	public PeriodicalUpdates(Gangland gangland) {
		this.gangland = gangland;
	}

	public PeriodicalUpdates(Gangland gangland, long interval) {
		this(gangland);
		this.repeatingTimer = new RepeatingTimer(gangland, interval, timer -> task());
	}

	private void task() {
		long    start = System.currentTimeMillis();
		boolean log   = SettingAddon.isAutoSaveDebug();

		// auto-saving
		if (log) Gangland.getLog4jLogger().info("Saving...");
		try {
			updatingDatabase();
			if (log) Gangland.getLog4jLogger().info("Data save complete");
		} catch (Throwable throwable) {
			Gangland.getLog4jLogger().error("There was an issue saving the data...");
		}

		// resetting player inventories
		if (log) Gangland.getLog4jLogger().info("Cache reset...");
		try {
			resetCache();
		} catch (Throwable exception) {
			Gangland.getLog4jLogger().error("There was an issue resetting the cache...", exception);
		}

		long end = System.currentTimeMillis();

		if (log) Gangland.getLog4jLogger().info(String.format("The process took %dms", end - start));
	}

	public void forceUpdate() {
		Gangland.getLog4jLogger().info("Force update...");
		task();
	}

	public void stop() {
		if (this.repeatingTimer != null) this.repeatingTimer.stop();
	}

	public void start() {
		if (this.repeatingTimer == null) return;

		Gangland.getLog4jLogger().info("Initializing auto-save...");
		this.repeatingTimer.start(true);
	}

	public void resetCache() {
		removeInventories();
	}

	public void updatingDatabase() {
		for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases()) {
			DatabaseHelper helper = new DatabaseHelper(gangland, handler);

			if (handler instanceof UserDatabase userDatabase) {
				// online users
				updateUserData(gangland.getInitializer().getUserManager(), helper, userDatabase);

				// offline users
				UserManager<OfflinePlayer> offlineUserManager = gangland.getInitializer().getOfflineUserManager();

				updateUserData(offlineUserManager, helper, userDatabase);
				offlineUserManager.clear();
			} else if (handler instanceof GangDatabase gangDatabase) {
				// gang data
				updateGangData(gangland.getInitializer().getGangManager(), helper, gangDatabase);

				// member data
				updateMemberData(gangland.getInitializer().getMemberManager(), helper, gangDatabase);
			} else if (handler instanceof RankDatabase rankDatabase) {
				// rank info
				updateRankData(gangland.getInitializer().getRankManager(), helper, rankDatabase);
			} else if (handler instanceof WaypointDatabase waypointDatabase) {
				// waypoint info
				updateWaypointData(gangland.getInitializer().getWaypointManager(), helper, waypointDatabase);
			}
		}
	}

	private void updateUserData(UserManager<? extends OfflinePlayer> userManager, DatabaseHelper helper,
	                            UserDatabase userDatabase) {
		helper.runQueries(database -> {
			for (User<? extends OfflinePlayer> user : userManager.getUsers().values()) {
				Object[] data = database.table("data").select("uuid = ?", new Object[]{user.getUser().getUniqueId()},
				                                              new int[]{Types.CHAR}, new String[]{"*"});

				if (data.length == 0) userDatabase.insertDataTable(user);
				else userDatabase.updateDataTable(user);

				Object[] bank = database.table("bank").select("uuid = ?", new Object[]{user.getUser().getUniqueId()},
				                                              new int[]{Types.CHAR}, new String[]{"*"});

				if (bank.length == 0) userDatabase.insertBankTable(user);
				else userDatabase.updateBankTable(user);
			}
		});
	}

	private void updateMemberData(MemberManager memberManager, DatabaseHelper helper, GangDatabase gangDatabase) {
		helper.runQueries(database -> {
			for (Member member : memberManager.getMembers().values()) {
				Object[] data = database.table("members").select("uuid = ?", new Object[]{member.getUuid()},
				                                                 new int[]{Types.CHAR}, new String[]{"*"});

				if (data.length == 0) gangDatabase.insertMemberTable(member);
				else gangDatabase.updateMembersTable(member);
			}
		});
	}

	private void updateGangData(GangManager gangManager, DatabaseHelper helper, GangDatabase gangDatabase) {
		helper.runQueries(database -> {
			for (Gang gang : gangManager.getGangs().values()) {
				Object[] data = database.table("data").select("id = ?", new Object[]{gang.getId()},
				                                              new int[]{Types.INTEGER}, new String[]{"*"});

				if (data.length == 0) gangDatabase.insertDataTable(gang);
				else gangDatabase.updateDataTable(gang);
			}
		});
	}

	private void updateRankData(RankManager rankManager, DatabaseHelper helper, RankDatabase rankDatabase) {
		helper.runQueries(database -> {
			for (Rank rank : rankManager.getRankTree()) {
				Object[] data = database.table("data").select("id = ?", new Object[]{rank.getUsedId()}, new int[]{
						Types.INTEGER
				}, new String[]{"*"});

				if (data.length == 0) rankDatabase.insertDataTable(rank);
				else rankDatabase.updateDataTable(rank);
			}
		});
	}

	private void updateWaypointData(WaypointManager waypointManager, DatabaseHelper helper,
	                                WaypointDatabase waypointDatabase) {
		helper.runQueries(database -> {
			for (Waypoint waypoint : waypointManager.getWaypoints().values()) {
				Object[] data = database.table("data").select("id = ?", new Object[]{waypoint.getUsedId()},
				                                              new int[]{Types.INTEGER}, new String[]{"*"});

				if (data.length == 0) waypointDatabase.insertDataTable(waypoint);
				else waypointDatabase.updateDataTable(waypoint);
			}
		});
	}

	private void removeInventories() {
		for (User<Player> user : gangland.getInitializer().getUserManager().getUsers().values())
			removeInventory(user);
	}

	private void removeInventory(User<Player> user) {
		if (userViewingInventory(user)) return;

		// remove the inventories
		user.clearInventories();
	}

	private boolean userViewingInventory(User<Player> user) {
		List<InventoryType> inventoryTypes = new ArrayList<>();

		// the plugin uses only anvil and chest so far
		inventoryTypes.add(InventoryType.ANVIL);
		inventoryTypes.add(InventoryType.CHEST);

		for (InventoryType type : inventoryTypes)
			if (user.getUser().getOpenInventory().getType() == type) return true;

		return false;
	}

}
