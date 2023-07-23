package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.account.gang.Gang;
import me.luckyraven.account.gang.Member;
import me.luckyraven.account.gang.MemberManager;
import me.luckyraven.account.type.Bank;
import me.luckyraven.bounty.Bounty;
import me.luckyraven.bounty.BountyEvent;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.Database;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.rank.RankManager;
import me.luckyraven.timer.RepeatingTimer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;


public final class CreateAccount implements Listener {

	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final Gangland            gangland;

	public CreateAccount(Gangland gangland) {
		this.gangland = gangland;
		this.memberManager = gangland.getInitializer().getMemberManager();
		this.userManager = gangland.getInitializer().getUserManager();
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player       player = event.getPlayer();
		User<Player> user   = new User<>(player);

		user.setBalance(SettingAddon.getPlayerInitialBalance());

		for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
			if (handler instanceof UserDatabase) {
				initializeUserData(user, new DatabaseHelper(gangland, handler));
				break;
			}
		// Add the user to a user manager group
		userManager.add(user);

		// need to check if the user already registered
		Member member = memberManager.getMember(player.getUniqueId());

		if (member != null) return;

		Member newMember = new Member(player.getUniqueId());
		for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
			if (handler instanceof GangDatabase) {
				initializeMemberData(newMember, new DatabaseHelper(gangland, handler));
				break;
			}
		memberManager.add(newMember);

	}

	public void initializeUserData(User<Player> user, DatabaseHelper helper) {
		helper.runQueries(database -> {
			// <--------------- Account Info --------------->
			Database config  = database.table("account");
			String[] columns = config.getColumns().toArray(String[]::new);

			// check for account table
			Object[] accountInfo = config.select("uuid = ?", new Object[]{user.getUser().getUniqueId()},
			                                     new int[]{Types.CHAR}, new String[]{"*"});
			// create player data into database
			if (accountInfo.length == 0) {
				config.insert(columns, new Object[]{
						user.getUser().getUniqueId(), user.getBalance(), user.getGangId()
				}, new int[]{Types.CHAR, Types.DOUBLE, Types.INTEGER});
			}
			// use player data
			else {
				user.setBalance((double) accountInfo[1]);
				user.setGangId((int) accountInfo[2]);

				if (user.hasGang()) {
					Gang gang = gangland.getInitializer().getGangManager().getGang(user.getGangId());
					user.addAccount(gang);
				}
			}

			// <--------------- Bank Info --------------->
			config = database.table("bank");
			columns = config.getColumns().toArray(String[]::new);

			// check for bank table
			Object[] bankInfo = config.select("uuid = ?", new Object[]{user.getUser().getUniqueId()},
			                                  new int[]{Types.CHAR}, new String[]{"*"});
			Bank bank = new Bank(user.getUser().getUniqueId(), user, "");
			// create player data into database
			if (bankInfo.length == 0) {
				config.insert(columns, new Object[]{
						user.getUser().getUniqueId(), bank.getName(), bank.getBalance()
				}, new int[]{Types.CHAR, Types.VARCHAR, Types.DOUBLE});
			}
			// use player data
			else {
				bank.setName(String.valueOf(bankInfo[1]));
				bank.setBalance((double) bankInfo[2]);
			}

			user.addAccount(bank);

			// <--------------- Data Info --------------->
			config = database.table("data");
			columns = config.getColumns().toArray(String[]::new);

			// check for data table
			Object[] dataInfo = config.select("uuid = ?", new Object[]{user.getUser().getUniqueId()},
			                                  new int[]{Types.CHAR}, new String[]{"*"});
			// create player data into database
			if (dataInfo.length == 0) {
				Date          joined        = new Date(user.getUser().getFirstPlayed());
				Instant       instant       = joined.toInstant();
				LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
				config.insert(columns, new Object[]{
						user.getUser().getUniqueId(), user.getKills(), user.getDeaths(), user.getMobKills(),
						user.hasBank(), user.getBounty(), localDateTime
				}, new int[]{
						Types.CHAR, Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.BOOLEAN, Types.DOUBLE, Types.DATE
				});
			}
			// use player data
			else {
				user.setKills((int) dataInfo[1]);
				user.setDeaths((int) dataInfo[2]);
				user.setMobKills((int) dataInfo[3]);
				user.setHasBank((boolean) dataInfo[4]);

				Bounty bounty = user.getBounty();
				bounty.setAmount((double) dataInfo[5]);

				if (bounty.hasBounty() && SettingAddon.isBountyTimerEnable()) {
					BountyEvent bountyEvent = new BountyEvent(user);

					if (bounty.getAmount() < SettingAddon.getBountyTimerMax()) {
						RepeatingTimer repeatingTimer = bounty.createTimer(gangland,
						                                                   SettingAddon.getBountyTimeInterval(),
						                                                   timer -> bountyExecutor(user, bountyEvent,
						                                                                           timer, helper));

						repeatingTimer.start();
					}

				}
			}
		});
	}

	public void initializeMemberData(Member member, DatabaseHelper helper) {
		helper.runQueries(database -> {
			Database config  = database.table("members");
			String[] columns = config.getColumns().toArray(String[]::new);

			Object[] membersInfo = config.select("uuid = ?", new Object[]{member.getUuid()}, new int[]{Types.CHAR},
			                                     new String[]{"*"});

			if (membersInfo.length == 0) {
				config.insert(columns, new Object[]{
						member.getUuid(), member.getGangId(), member.getContribution(), member.getRank(),
						member.getGangJoinDate()
				}, new int[]{Types.CHAR, Types.INTEGER, Types.DOUBLE, Types.VARCHAR, Types.BIGINT});
			} else {
				RankManager rankManager = gangland.getInitializer().getRankManager();
				member.setGangId((int) membersInfo[1]);
				member.setContribution((double) membersInfo[2]);
				member.setRank(rankManager.get(String.valueOf(membersInfo[3])));
				member.setGangJoinDate((long) membersInfo[4]);
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
