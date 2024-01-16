package me.luckyraven.data.user;

import com.google.common.base.Preconditions;
import me.luckyraven.Gangland;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.type.Bank;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.database.Database;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.feature.bounty.Bounty;
import me.luckyraven.feature.bounty.BountyEvent;
import me.luckyraven.feature.level.Level;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.timer.RepeatingTimer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.jetbrains.annotations.NotNull;

import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserManager<T extends OfflinePlayer> {

	private final Gangland        gangland;
	private final Map<T, User<T>> users;

	public UserManager(Gangland gangland) {
		this.gangland = gangland;
		this.users = new HashMap<>();
	}

	public void initializeUserData(User<? extends OfflinePlayer> user, UserDatabase userDatabase) {
		DatabaseHelper helper = new DatabaseHelper(gangland, userDatabase);

		helper.runQueries(database -> {
			// <--------------- Bank Info --------------->
			Database bankTable = database.table("bank");

			// check for bank table
			Object[] bankInfo =
					bankTable.select("uuid = ?", new Object[]{user.getUser().getUniqueId()}, new int[]{Types.CHAR},
									 new String[]{"*"});
			Bank bank = new Bank(user, "");
			// create player data into database
			if (bankInfo.length == 0) {
				if (!SettingAddon.isAutoSave()) userDatabase.insertBankTable(user);
			} else {
				String name    = String.valueOf(bankInfo[1]);
				double balance = (double) bankInfo[2];

				bank.setName(name);
				bank.getEconomy().setBalance(balance);
			}

			user.addAccount(bank);

			// <--------------- Data Info --------------->
			Database dataTable = database.table("data");

			// check for data table
			Object[] dataInfo =
					dataTable.select("uuid = ?", new Object[]{user.getUser().getUniqueId()}, new int[]{Types.CHAR},
									 new String[]{"*"});
			// create player data into a database
			if (dataInfo.length == 0) {
				if (!SettingAddon.isAutoSave()) userDatabase.insertDataTable(user);
			} else {
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

				if (!(userBounty.hasBounty() && SettingAddon.isBountyTimerEnabled())) return;

				BountyEvent bountyEvent = new BountyEvent(userBounty);
				bountyEvent.setUserBounty(user);

				if (userBounty.getAmount() >= SettingAddon.getBountyTimerMax()) return;

				RepeatingTimer repeatingTimer = userBounty.createTimer(gangland, SettingAddon.getBountyTimeInterval(),
																	   timer -> bountyExecutor(user, bountyEvent, timer,
																							   helper));
				repeatingTimer.start(false);
			}
		});
	}

	public void initializeUserPermission(User<Player> user, Member member) {
		Rank rank = member.getRank();

		if (rank == null) return;

		// attach all the permissions when the user has the specified rank
		PermissionAttachment attachment = user.getUser().addAttachment(gangland);
		user.setPermissionAttachment(attachment);

		for (String perm : rank.getPermissions())
			attachment.setPermission(perm, true);

		// apparently updates the command list according to the permission list
		user.getUser().updateCommands();
	}

	private void bountyExecutor(User<? extends OfflinePlayer> user, BountyEvent bountyEvent, RepeatingTimer timer,
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

	public void add(User<T> user) {
		users.put(user.getUser(), user);
	}

	public void remove(@NotNull User<T> user) {
		Preconditions.checkArgument(user != null, "User can't be null!");

		users.remove(user.getUser());
	}

	public void clear() {
		users.clear();
	}

	public boolean contains(User<T> user) {
		if (user == null) return false;
		return users.containsKey(user.getUser());
	}

	public User<T> getUser(T userPred) {
		return users.get(userPred);
	}

	public int size() {
		return users.size();
	}

	/**
	 * Creates a new instance of similar data set
	 *
	 * @return new HashSet of users
	 */
	public Map<T, User<T>> getUsers() {
		return new HashMap<>(users);
	}

	@Override
	public String toString() {
		Map<T, User<T>> userMap = users;
		List<String>    users   = userMap.values().stream().map(User::toString).toList();
		return "users=" + users;
	}

}
