package me.luckyraven;

import me.luckyraven.account.gang.Gang;
import me.luckyraven.account.gang.GangManager;
import me.luckyraven.account.gang.Member;
import me.luckyraven.account.gang.MemberManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.database.sub.RankDatabase;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.rank.Rank;
import me.luckyraven.rank.RankManager;
import me.luckyraven.timer.RepeatingTimer;
import org.bukkit.entity.Player;

import java.sql.Types;

public class PeriodicalUpdates {

	private final Gangland       gangland;
	private final RepeatingTimer repeatingTimer;

	public PeriodicalUpdates(Gangland gangland, long interval) {
		this.gangland = gangland;
		this.repeatingTimer = new RepeatingTimer(gangland, interval, (timer) -> {
			long start = System.currentTimeMillis();

			// auto-saving
			gangland.getLogger().info("Auto-saving...");
			try {
				updatingDatabase();
				gangland.getLogger().info("Auto-save complete");
			} catch (Exception exception) {
				gangland.getLogger().warning("There was an issue auto-saving the data...");
				exception.printStackTrace();
			}

			// resetting player inventories
			// TODO

			long end = System.currentTimeMillis();

			gangland.getLogger().info(String.format("The process took %dms", end - start));
		});
	}

	public void forceUpdate() {
		gangland.getLogger().info("Force update...");
		this.repeatingTimer.runTask();
	}

	public void stop() {
		this.repeatingTimer.stop();
	}

	public void start() {
		gangland.getLogger().info("Initialized auto-save...");
		this.repeatingTimer.startAsync();
	}

	private void updatingDatabase() {
		for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases()) {
			DatabaseHelper helper = new DatabaseHelper(gangland, handler);

			if (handler instanceof UserDatabase userDatabase) {
				updateUserData(gangland.getInitializer().getUserManager(), helper, userDatabase);
			} else if (handler instanceof GangDatabase gangDatabase) {
				updateGangData(gangland.getInitializer().getGangManager(), helper, gangDatabase);
				updateMemberData(gangland.getInitializer().getMemberManager(), helper, gangDatabase);
			} else if (handler instanceof RankDatabase rankDatabase) {
				updateRankData(gangland.getInitializer().getRankManager(), helper, rankDatabase);
			}
		}
	}

	private void updateUserData(UserManager<Player> userManager, DatabaseHelper helper, UserDatabase userDatabase) {
		helper.runQueries(database -> {
			for (User<Player> user : userManager.getUsers().values()) {
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

}
