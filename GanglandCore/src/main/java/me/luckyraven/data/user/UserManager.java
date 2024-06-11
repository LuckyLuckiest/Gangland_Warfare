package me.luckyraven.data.user;

import com.google.common.base.Preconditions;
import me.luckyraven.Gangland;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.account.type.Bank;
import me.luckyraven.data.rank.Permission;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.database.tables.BankTable;
import me.luckyraven.database.tables.UserTable;
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
		this.users    = new HashMap<>();
	}

	public void initializeUserData(User<? extends OfflinePlayer> user, UserTable userTable, BankTable bankTable) {
		DatabaseHelper helper = new DatabaseHelper(gangland, gangland.getInitializer().getGanglandDatabase());

		helper.runQueries(database -> {
			// <--------------- Bank Info --------------->
			Object[] bankData = database.table(bankTable.getName())
										.select("uuid = ?", new Object[]{user.getUser().getUniqueId()},
												new int[]{Types.CHAR}, new String[]{"*"});

			// check for bank table
			Bank bank = new Bank(user, "");
			// create player data into database
			if (bankData.length == 0) {
				if (!SettingAddon.isAutoSave()) bankTable.insertTableQuery(database, user);
			} else {
				String name    = String.valueOf(bankData[1]);
				double balance = (double) bankData[2];

				bank.setName(name);
				bank.getEconomy().setBalance(balance);
			}

			user.addAccount(bank);

			// <--------------- Data Info --------------->
			Object[] userData = database.table(userTable.getName())
										.select("uuid = ?", new Object[]{user.getUser().getUniqueId()},
												new int[]{Types.CHAR}, new String[]{"*"});

			// create player data into a database
			if (userData.length == 0) {
				if (!SettingAddon.isAutoSave()) userTable.insertTableQuery(database, user);
			} else {
				int    v          = 1;
				double balance    = (double) userData[v++];
				int    kills      = (int) userData[v++];
				int    deaths     = (int) userData[v++];
				int    mobKills   = (int) userData[v++];
				double bounty     = (double) userData[v++];
				int    level      = (int) userData[v++];
				double experience = (double) userData[v++];
				int    wanted     = (int) userData[v];

				user.setKills(kills);
				user.setDeaths(deaths);
				user.setMobKills(mobKills);
				user.getEconomy().setBalance(balance);
				user.getWanted().setLevel(wanted);

				// get the gang id from the member manager
				MemberManager memberManager = gangland.getInitializer().getMemberManager();

				user.setGangId(memberManager.getMember(user.getUuid()).getGangId());

				// check for the availability of the bank from the accounts connected to the user
				boolean hasBank = user.getLinkedAccounts()
									  .stream()
									  .anyMatch(account -> account instanceof Bank);

				user.setHasBank(hasBank);

				if (user.hasGang()) {
					GangManager gangManager = gangland.getInitializer().getGangManager();
					Gang        gang        = gangManager.getGang(user.getGangId());

					user.addAccount(gang);
				}

				// set the level of the user
				Level userLevel = user.getLevel();

				userLevel.setLevelValue(level);
				userLevel.setExperience(experience);

				// set the bounty value of the user
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

		for (Permission perm : rank.getPermissions())
			attachment.setPermission(perm.getPermission(), true);

		// apparently updates the command list according to the permission list
		user.getUser().updateCommands();
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

}
