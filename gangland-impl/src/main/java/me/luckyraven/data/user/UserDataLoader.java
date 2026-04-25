package me.luckyraven.data.user;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.core.feature.Executor;
import me.luckyraven.core.timer.Timer;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.tables.player.BankTable;
import me.luckyraven.database.tables.player.UserTable;
import me.luckyraven.economy.Currency;
import me.luckyraven.economy.bank.Bank;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.gang.bounty.Bounty;
import me.luckyraven.gang.bounty.BountyExecutor;
import me.luckyraven.gang.bounty.BountySettings;
import me.luckyraven.gang.events.bounty.BountyEvent;
import me.luckyraven.gang.events.user.UserBountyEvent;
import me.luckyraven.gang.events.wanted.WantedEvent;
import me.luckyraven.gang.member.Member;
import me.luckyraven.gang.member.MemberManager;
import me.luckyraven.gang.user.Level;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.wanted.Wanted;
import me.luckyraven.gang.wanted.WantedExecutor;
import me.luckyraven.gang.wanted.WantedSettings;
import me.luckyraven.persistence.database.DatabaseHelper;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Impl-side loader that hydrates a freshly-constructed {@link User} from the database. Lives in gangland-impl because
 * it consumes the concrete {@link UserTable} and {@link BankTable} types (specifically
 * {@link BankTable#searchCriteria(User)}, which isn't part of the abstract {@code Table} contract). The
 * {@link me.luckyraven.gang.user.UserManager} in the gang module stays table-agnostic and focuses on cache management.
 */
@CustomLog
public final class UserDataLoader {

	private final Gangland         gangland;
	private final GanglandDatabase database;
	private final MemberManager    memberManager;
	private final BountySettings   bountySettings;
	private final WantedSettings   wantedSettings;

	public UserDataLoader(Gangland gangland,
	                      GanglandDatabase database,
	                      MemberManager memberManager,
	                      BountySettings bountySettings,
	                      WantedSettings wantedSettings) {
		this.gangland       = gangland;
		this.database       = database;
		this.memberManager  = memberManager;
		this.bountySettings = bountySettings;
		this.wantedSettings = wantedSettings;
	}

	private static Instant parseInstant(Object raw) {
		if (raw == null) return null;
		try {
			return Instant.parse(String.valueOf(raw));
		} catch (DateTimeParseException ignored) {
			return null;
		}
	}

	public <T extends OfflinePlayer> void loadUserData(User<T> user, UserTable userTable, BankTable bankTable) {
		DatabaseHelper helper = new DatabaseHelper(gangland, database);

		helper.runQueries(db -> {
			Map<String, Object> userSearch = userTable.searchCriteria(user);
			Object[] userData = db.table(userTable.getName())
			                      .select((String) userSearch.get("search"), (Object[]) userSearch.get("info"),
			                              (int[]) userSearch.get("type"), new String[]{"*"});

			if (userData.length == 0) {
				if (!Settings.isAutoSave()) userTable.insertTableQuery(db, user);
				return;
			}

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
			user.getEconomy().setAmount(Currency.of(balance));
			user.getWanted().setLevel(wanted);

			Member member = memberManager.getMember(user.getUuid());
			if (member != null) {
				user.setGangId(member.getGangId());
			}

			Map<String, Object> bankSearch = bankTable.searchCriteria(user);
			Object[] bankData = db.table(bankTable.getName())
			                      .select((String) bankSearch.get("search"), (Object[]) bankSearch.get("info"),
			                              (int[]) bankSearch.get("type"), new String[]{"*"});

			if (bankData.length != 0) {
				// BankTable column order (see BankTable.java): uuid, name, balance, tier_id, deposited_today,
				// cap_reset_at, last_interest_at, last_weekly_loan_at, last_monthly_loan_at. Reads have to cover
				// every column or the cap / tier / rolling counter / interest timestamp gets zeroed on every login.
				String     name        = String.valueOf(bankData[1]);
				BigDecimal bankBalance = Currency.ofYaml(bankData[2]);

				Object rawTier = bankData.length > 3 ? bankData[3] : null;
				String tierId  = rawTier == null ? null : String.valueOf(rawTier);

				double depositedToday = bankData.length > 4 && bankData[4] != null
				                        ? ((Number) bankData[4]).doubleValue() : 0D;

				Instant capResetAt   = parseInstant(bankData.length > 5 ? bankData[5] : null);
				Instant lastInterest = parseInstant(bankData.length > 6 ? bankData[6] : null);
				Instant weeklyAt     = parseInstant(bankData.length > 7 ? bankData[7] : null);
				Instant monthlyAt    = parseInstant(bankData.length > 8 ? bankData[8] : null);

				Bank bank = new Bank(user.getUuid(), name);
				bank.getEconomy().setAmount(bankBalance);
				bank.setTierId(tierId);
				bank.setDepositedToday(depositedToday);
				bank.setCapResetAt(capResetAt);
				bank.setLastInterestAt(lastInterest);
				bank.setLastWeeklyLoanAt(weeklyAt);
				bank.setLastMonthlyLoanAt(monthlyAt);
				user.setBank(bank);
			}

			Level userLevel = user.getLevel();
			userLevel.setLevelValue(level);
			userLevel.setExperience(experience);

			Bounty userBounty = user.getBounty();
			userBounty.setAmount(Currency.of(bounty));

			if (!user.getUser().isOnline()) return;

			if (userBounty.hasBounty() && Settings.isBountyTimerEnabled()) {
				BountyEvent bountyEvent = new UserBountyEvent(true, user);

				if (userBounty.getAmount().compareTo(BigDecimal.valueOf(Settings.getBountyTimerMax())) < 0) {
					Executor executor = new BountyExecutor(gangland, bountyEvent, user, bountySettings);
					Timer    timer    = executor.createTimer();
					timer.start(true);
				}
			}

			Wanted userWanted = user.getWanted();
			if (userWanted.isWanted() && Settings.isWantedTimerEnabled()) {
				WantedEvent wantedEvent = new WantedEvent(true, userWanted);
				Executor    executor    = new WantedExecutor(gangland, wantedEvent, user, wantedSettings);
				Timer       timer       = executor.createTimer();
				timer.start(true);
			}
		});
	}
}
