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
import me.luckyraven.level.Level;
import me.luckyraven.rank.Rank;
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

	// Need to create the account before any other event
	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player       player = event.getPlayer();
		User<Player> user   = new User<>(player);

		user.getEconomy().setBalance(SettingAddon.getUserInitialBalance());

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
			// <--------------- Bank Info --------------->
			Database bankTable   = database.table("bank");
			String[] bankColumns = bankTable.getColumns().toArray(String[]::new);

			// check for bank table
			Object[] bankInfo = bankTable.select("uuid = ?", new Object[]{user.getUser().getUniqueId()},
			                                     new int[]{Types.CHAR}, new String[]{"*"});
			Bank bank = new Bank(user, "");
			// create player data into database
			if (bankInfo.length == 0) {
				bankTable.insert(bankColumns, new Object[]{
						user.getUser().getUniqueId(), bank.getName(), bank.getEconomy().getBalance()
				}, new int[]{Types.CHAR, Types.VARCHAR, Types.DOUBLE});
			}
			// use player data
			else {
				String name    = String.valueOf(bankInfo[1]);
				double balance = (double) bankInfo[2];

				bank.setName(name);
				bank.getEconomy().setBalance(balance);
			}

			user.addAccount(bank);

			// <--------------- Data Info --------------->
			Database dataTable   = database.table("data");
			String[] dataColumns = dataTable.getColumns().toArray(String[]::new);

			// check for data table
			Object[] dataInfo = dataTable.select("uuid = ?", new Object[]{user.getUser().getUniqueId()},
			                                     new int[]{Types.CHAR}, new String[]{"*"});
			// create player data into database
			if (dataInfo.length == 0) {
				Date          joined        = new Date(user.getUser().getFirstPlayed());
				Instant       instant       = joined.toInstant();
				LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
				dataTable.insert(dataColumns, new Object[]{
						user.getUser().getUniqueId(), user.getKills(), user.getDeaths(), user.getMobKills(),
						user.getGangId(), user.isHasBank(), user.getEconomy().getBalance(), user.getBounty(),
						user.getLevel().getExperience(), localDateTime
				}, new int[]{
						Types.CHAR, Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.BOOLEAN,
						Types.DOUBLE, Types.DOUBLE, Types.DOUBLE, Types.DATE
				});
			}
			// use player data
			else {
				int     kills    = (int) dataInfo[1];
				int     deaths   = (int) dataInfo[2];
				int     mobKills = (int) dataInfo[3];
				int     gangId   = (int) dataInfo[4];
				boolean hasBank  = (boolean) dataInfo[5];
				double  balance  = (double) dataInfo[6];
				double  bounty   = (double) dataInfo[7];
				double  level    = (double) dataInfo[8];

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
				userLevel.setExperience(level);

				Bounty userBounty = user.getBounty();
				userBounty.setAmount(bounty);

				if (userBounty.hasBounty() && SettingAddon.isBountyTimerEnable()) {
					BountyEvent bountyEvent = new BountyEvent(userBounty);
					bountyEvent.setUserBounty(user);

					if (userBounty.getAmount() < SettingAddon.getBountyTimerMax()) {
						RepeatingTimer repeatingTimer = userBounty.createTimer(gangland,
						                                                       SettingAddon.getBountyTimeInterval(),
						                                                       timer -> bountyExecutor(user,
						                                                                               bountyEvent,
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

			Object[] memberInfo = config.select("uuid = ?", new Object[]{member.getUuid()}, new int[]{Types.CHAR},
			                                    new String[]{"*"});

			if (memberInfo.length == 0) {
				config.insert(columns, new Object[]{
						member.getUuid(), member.getGangId(), member.getContribution(), member.getRank(),
						member.getGangJoinDate()
				}, new int[]{Types.CHAR, Types.INTEGER, Types.DOUBLE, Types.VARCHAR, Types.BIGINT});
			} else {
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
