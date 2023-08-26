package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.account.type.Bank;
import me.luckyraven.data.bounty.Bounty;
import me.luckyraven.data.bounty.BountyEvent;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.Database;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.data.level.Level;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.util.timer.RepeatingTimer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.Types;

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

		for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
			if (handler instanceof UserDatabase userDatabase) {
				initializeUserData(user, userDatabase);
				break;
			}
		// Add the user to a user manager group
		userManager.add(user);

		user.getScoreboard().start();

		// need to check if the user already registered
		Member member = memberManager.getMember(player.getUniqueId());

		if (member != null) return;

		Member newMember = new Member(player.getUniqueId());
		for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
			if (handler instanceof GangDatabase gangDatabase) {
				initializeMemberData(newMember, gangDatabase);
				break;
			}

		memberManager.add(newMember);
	}

	public void initializeUserData(User<Player> user, UserDatabase userDatabase) {
		DatabaseHelper helper = new DatabaseHelper(gangland, userDatabase);

		helper.runQueries(database -> {
			// <--------------- Bank Info --------------->
			Database bankTable = database.table("bank");

			// check for bank table
			Object[] bankInfo = bankTable.select("uuid = ?", new Object[]{user.getUser().getUniqueId()},
			                                     new int[]{Types.CHAR}, new String[]{"*"});
			Bank bank = new Bank(user, "");
			// create player data into database
			if (bankInfo.length == 0) if (!SettingAddon.isAutoSave()) userDatabase.insertBankTable(user);
			else {
				String name    = String.valueOf(bankInfo[1]);
				double balance = (double) bankInfo[2];

				bank.setName(name);
				bank.getEconomy().setBalance(balance);
			}

			user.addAccount(bank);

			// <--------------- Data Info --------------->
			Database dataTable = database.table("data");

			// check for data table
			Object[] dataInfo = dataTable.select("uuid = ?", new Object[]{user.getUser().getUniqueId()},
			                                     new int[]{Types.CHAR}, new String[]{"*"});
			// create player data into a database
			if (dataInfo.length == 0) if (!SettingAddon.isAutoSave()) userDatabase.insertDataTable(user);
			else {
				int     kills      = (int) dataInfo[1];
				int     deaths     = (int) dataInfo[2];
				int     mobKills   = (int) dataInfo[3];
				int     gangId     = (int) dataInfo[4];
				boolean hasBank    = (boolean) dataInfo[5];
				double  balance    = (double) dataInfo[6];
				double  bounty     = (double) dataInfo[7];
				int     level      = (int) dataInfo[8];
				double  experience = (double) dataInfo[9];

				user.setKills(kills);
				user.setDeaths(deaths);
				user.setMobKills(mobKills);
				user.setGangId(gangId);
				user.setHasBank(hasBank);
				user.getEconomy().setBalance(balance);

				if (user.hasGang()) {
					Gang gang = gangland.getInitializer().getGangManager().getGang(user.getGangId());
					user.addAccount(gang);
				}

				Level userLevel = user.getLevel();
				userLevel.setLevelValue(level);
				userLevel.setExperience(experience);

				Bounty userBounty = user.getBounty();
				userBounty.setAmount(bounty);

				if (!(userBounty.hasBounty() && SettingAddon.isBountyTimerEnable())) return;

				BountyEvent bountyEvent = new BountyEvent(userBounty);
				bountyEvent.setUserBounty(user);

				if (userBounty.getAmount() >= SettingAddon.getBountyTimerMax()) return;

				RepeatingTimer repeatingTimer = userBounty.createTimer(gangland, SettingAddon.getBountyTimeInterval(),
				                                                       timer -> bountyExecutor(user, bountyEvent, timer,
				                                                                               helper));
				repeatingTimer.start();
			}
		});
	}

	public void initializeMemberData(Member member, GangDatabase gangDatabase) {
		DatabaseHelper helper = new DatabaseHelper(gangland, gangDatabase);

		helper.runQueries(database -> {
			Database config = database.table("members");

			Object[] memberInfo = config.select("uuid = ?", new Object[]{member.getUuid()}, new int[]{Types.CHAR},
			                                    new String[]{"*"});

			// create member data into a database
			if (memberInfo.length == 0) if (!SettingAddon.isAutoSave()) gangDatabase.insertMemberTable(member);
			else {
				RankManager rankManager = gangland.getInitializer().getRankManager();

				int    gangId       = (int) memberInfo[1];
				double contribution = (double) memberInfo[2];
				Rank   rank         = rankManager.get(String.valueOf(memberInfo[3]));
				long   gangJoin     = (long) memberInfo[4];

				member.setGangId(gangId);
				member.setContribution(contribution);
				member.setRank(rank);
				member.setGangJoinDateLong(gangJoin);
			}
		});
	}

	private void bountyExecutor(User<Player> user, BountyEvent bountyEvent, RepeatingTimer timer,
	                            DatabaseHelper helper) {
		Bounty bounty    = user.getBounty();
		double oldAmount = bounty.getAmount();

		if (bounty.getAmount() >= SettingAddon.getBountyTimerMax()) timer.stop();
		else {
			double amount = oldAmount * SettingAddon.getBountyTimerMultiple();
			bountyEvent.setAmountApplied(amount - oldAmount);

			// call the event
			gangland.getServer().getPluginManager().callEvent(bountyEvent);

			if (!bountyEvent.isCancelled())
				// change the amount
				bounty.setAmount(amount);

			// update the database
			UserDatabase   userDatabase = (UserDatabase) helper.getDatabaseHandler();
			DatabaseHelper help         = new DatabaseHelper(gangland, userDatabase);

			help.runQueries(db -> userDatabase.updateDataTable(user));
		}
	}

}
