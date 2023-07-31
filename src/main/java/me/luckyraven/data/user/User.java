package me.luckyraven.data.user;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.account.Account;
import me.luckyraven.bounty.Bounty;
import me.luckyraven.rank.Rank;
import me.luckyraven.wanted.Wanted;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles all users registered data, only for online users.
 *
 * @param <T> type of the user
 */
public class User<T> {


	private final @Getter T user;

	private final List<Account<?, ?>> linkedAccounts;

	private @Getter
	@Setter int kills, deaths, mobKills, gangId;
	private @Getter
	@Setter double balance;
	private @Setter boolean hasBank;

	private @Getter Bounty bounty;

	private @Getter
	@Setter Rank   rank;
	private @Getter
	@Setter Wanted wanted;


	/**
	 * Instantiates a new Database.
	 *
	 * @param user     the user
	 * @param kills    the kills
	 * @param deaths   the deaths
	 * @param mobKills the mob kills
	 * @param balance  the balance
	 * @param hasBank  they had a bank
	 * @param gangId   the gang id
	 */
	public User(T user, int kills, int deaths, int mobKills, double balance, boolean hasBank, int gangId) {
		this(user, kills, deaths, mobKills, balance, hasBank);
		this.gangId = gangId;
	}

	/**
	 * Instantiates a new Database.
	 *
	 * @param user     the user
	 * @param kills    the kills
	 * @param deaths   the deaths
	 * @param mobKills the mob kills
	 * @param balance  the balance
	 * @param hasBank  they had a bank
	 */
	public User(T user, int kills, int deaths, int mobKills, double balance, boolean hasBank) {
		this(user);
		this.kills = kills;
		this.deaths = deaths;
		this.mobKills = mobKills;
		this.balance = balance;
		this.hasBank = hasBank;
	}

	/**
	 * Instantiates a new Database.
	 *
	 * @param user the user
	 */
	public User(T user) {
		this.user = user;
		this.kills = 0;
		this.deaths = 0;
		this.mobKills = 0;
		this.balance = 0D;
		this.hasBank = false;
		this.gangId = -1;
		this.bounty = new Bounty();
		this.wanted = new Wanted();
		this.linkedAccounts = new ArrayList<>();
	}

	public void resetGang() {
		this.gangId = -1;
	}

	public boolean hasGang() {
		return this.gangId != -1;
	}

	public boolean hasBank() {
		return hasBank;
	}

	public void addAccount(Account<?, ?> account) {
		linkedAccounts.add(account);
	}

	public void removeAccount(Account<?, ?> account) {
		linkedAccounts.remove(account);
	}

	public List<Account<?, ?>> getLinkedAccounts() {
		return new ArrayList<>(linkedAccounts);
	}

	/**
	 * Gets a kills/deaths ratio of the user.
	 *
	 * @return the kd ratio
	 */
	public double getKillDeathRatio() {
		return deaths == 0 ? 0D : (double) kills / deaths;
	}

	@Override
	public String toString() {
		return String.format("User:{Data=%s,KD=%.2f,bal=%.2f,gangId=%d}", user, getKillDeathRatio(), balance, gangId);
	}

}
