package org.luckyraven.gangland.data.user;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.core.feature.Executor;
import org.luckyraven.keystone.timer.Timer;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.database.tables.player.BankTable;
import org.luckyraven.gangland.database.tables.player.UserTable;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.bank.Bank;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.core.bounty.Bounty;
import org.luckyraven.gangland.core.bounty.BountyExecutor;
import org.luckyraven.gangland.core.bounty.BountySettings;
import org.luckyraven.gangland.core.events.bounty.BountyEvent;
import org.luckyraven.gangland.core.events.user.UserBountyEvent;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedSettings;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.keystone.persistence.database.DatabaseHelper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Impl-side loader that hydrates a freshly-constructed {@link User} from the database. Lives in gangland-impl because
 * it consumes the concrete {@link UserTable} and {@link BankTable} types (specifically
 * {@link BankTable#searchCriteria(User)}, which isn't part of the abstract {@code Table} contract). The gang
 * membership fact ({@code gangId}) is read through the always-present {@link GangMembership} holder (R9) rather
 * than the gang module's {@code MemberManager}, which impl can never depend on.
 */
@CustomLog
public final class UserDataLoader {

	private final Gangland         gangland;
	private final GanglandDatabase database;
	private final GangMembership   gangMembership;
	private final BountySettings   bountySettings;
	private final WantedStars      wantedStars;

	public UserDataLoader(Gangland gangland,
	                      GanglandDatabase database,
	                      GangMembership gangMembership,
	                      BountySettings bountySettings,
	                      WantedSettings wantedSettings,
	                      WantedStars wantedStars) {
		this.gangland       = gangland;
		this.database       = database;
		this.gangMembership = gangMembership;
		this.bountySettings = bountySettings;
		this.wantedStars    = wantedStars;
	}

	private static Instant parseInstant(Object raw) {
		if (raw == null) return null;
		try {
			return Instant.parse(String.valueOf(raw));
		} catch (DateTimeParseException ignored) {
			return null;
		}
	}

	public static final int  LOAD_ATTEMPTS    = 3;
	public static final long RETRY_DELAY_MILLIS = 500L;

	/**
	 * Hydrates the user, retrying a failed read up to {@link #LOAD_ATTEMPTS} times {@link #RETRY_DELAY_MILLIS} apart
	 * (blocks the calling thread, so call it off the main thread). Keystone's {@code runQueries} swallows a
	 * {@code SQLException}, so success is "the read ran to the end" (T-286).
	 *
	 * @return {@code false} when every attempt failed; the user then still holds defaults and must not be saved
	 */
	public <T extends OfflinePlayer> boolean loadUserData(User<T> user, UserTable userTable, BankTable bankTable) {
		for (int attempt = 1; attempt <= LOAD_ATTEMPTS; attempt++) {
			if (tryLoad(user, userTable, bankTable)) return true;

			if (attempt == LOAD_ATTEMPTS) break;
			log.warn("User data read failed for {} (attempt {}/{}); retrying", user.getUuid(), attempt, LOAD_ATTEMPTS);
			try {
				Thread.sleep(RETRY_DELAY_MILLIS);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		log.error("User data for {} could not be read after {} attempts", user.getUuid(), LOAD_ATTEMPTS);
		return false;
	}

	private <T extends OfflinePlayer> boolean tryLoad(User<T> user, UserTable userTable, BankTable bankTable) {
		DatabaseHelper helper = new DatabaseHelper(gangland, database);
		boolean[]      ran    = {false};

		helper.runQueries(db -> {
			Map<String, Object> userSearch = userTable.searchCriteria(user);
			Object[] userData = db.table(userTable.getName())
			                      .select((String) userSearch.get("search"), (Object[]) userSearch.get("info"),
			                              (int[]) userSearch.get("type"), new String[]{"*"});

			if (userData.length == 0) {
				// First-ever join: the DB has now confirmed there is no saved balance, so this is the only safe
				// point to stamp the configured starting balance. EconomyHandler.setAmount clears the backing
				// Vault account before re-depositing, so doing this on every join (as CreateAccountListener used
				// to) zeroed a returning player's money until the read came back - permanently when it failed.
				user.getEconomy().setAmount(Settings.getUserInitialBalance());

				if (!Settings.isAutoSave()) userTable.insertTableQuery(db, user);
				ran[0] = true;
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
			int    wanted     = (int) userData[v++];
			// NULL = a row saved before the ledger existed: the whole bounty counts as posted once
			String posters    = userData.length > v && userData[v] != null ? String.valueOf(userData[v]) : null;

			user.setKills(kills);
			user.setDeaths(deaths);
			user.setMobKills(mobKills);
			user.getEconomy().setAmount(Currency.of(balance));
			// RESTORE cause, and the decay clock for an online owner (hops to the main thread when needed)
			if (wanted > 0) wantedStars.restore(user, wanted);

			int gangId = gangMembership.gangIdOf(user.getUuid());
			if (gangId != -1) {
				user.setGangId(gangId);
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

			// the ledger is main-thread state: a post or a kill can book into it while this row loads, so the saved
			// bounty is merged under it there instead of wiping it from this thread
			Runnable restoreBounty = () -> restoreBounty(user, bounty, posters);
			if (Bukkit.isPrimaryThread()) restoreBounty.run();
			else Bukkit.getScheduler().runTask(gangland, restoreBounty);
			ran[0] = true;
		});

		return ran[0];
	}

	private void restoreBounty(User<? extends OfflinePlayer> user, double saved, @Nullable String posters) {
		Bounty userBounty = user.getBounty();
		userBounty.restoreSaved(Currency.of(saved), posters);

		if (!user.getUser().isOnline()) return;

		// only the server-made part compounds; the timer touches Bukkit (event), so it starts on the main thread
		if (Settings.isBountyTimerEnabled() && userBounty.getNotoriety().signum() > 0
		    && userBounty.getNotoriety().compareTo(BigDecimal.valueOf(Settings.getBountyTimerMax())) < 0) {
			BountyEvent bountyEvent = new UserBountyEvent(false, user);
			Executor    executor    = new BountyExecutor(gangland, bountyEvent, user, bountySettings);

			executor.createTimer().start(false);
		}
	}
}
