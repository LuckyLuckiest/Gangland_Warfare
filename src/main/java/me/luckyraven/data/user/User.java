package me.luckyraven.data.user;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.bukkit.scoreboard.Scoreboard;
import me.luckyraven.data.account.Account;
import me.luckyraven.data.economy.EconomyHandler;
import me.luckyraven.feature.bounty.Bounty;
import me.luckyraven.feature.level.Level;
import me.luckyraven.feature.phone.Phone;
import me.luckyraven.feature.wanted.Wanted;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles all users registered data, only for online users.
 *
 * @param <T> type of the user
 */
@Getter
public class User<T extends OfflinePlayer> {


	private final T                   user;
	private final List<Account<?, ?>> linkedAccounts;
	private final Bounty              bounty;
	private final Level               level;
	private final Wanted              wanted;
	private final EconomyHandler      economy;

	private @Setter int kills, deaths, mobKills, gangId;
	private @Setter boolean    hasBank;
	private @Setter Phone      phone;
	private @Setter Scoreboard scoreboard;

	/**
	 * Instantiates a new Database.
	 *
	 * @param user     the user
	 * @param kills    the kills
	 * @param deaths   the deaths
	 * @param mobKills the mob kills
	 * @param hasBank  they had a bank
	 * @param gangId   the gang id
	 */
	public User(T user, int kills, int deaths, int mobKills, boolean hasBank, int gangId) {
		this(user, kills, deaths, mobKills, hasBank);
		this.gangId = gangId;
	}

	/**
	 * Instantiates a new Database.
	 *
	 * @param user     the user
	 * @param kills    the kills
	 * @param deaths   the deaths
	 * @param mobKills the mob kills
	 * @param hasBank  they had a bank
	 */
	public User(T user, int kills, int deaths, int mobKills, boolean hasBank) {
		this(user);
		this.kills = kills;
		this.deaths = deaths;
		this.mobKills = mobKills;
		this.hasBank = hasBank;
	}

	/**
	 * Instantiates a new Database.
	 *
	 * @param user the user
	 */
	@SuppressWarnings("unchecked")
	public User(T user) {
		this.user = user;
		this.kills = this.deaths = this.mobKills = 0;
		this.hasBank = false;
		this.gangId = -1;
		this.linkedAccounts = new ArrayList<>();
		this.level = new Level();
		this.bounty = new Bounty();
		this.wanted = new Wanted();
		this.economy = new EconomyHandler(this);
		if (user instanceof Player) this.scoreboard = new Scoreboard((User<Player>) this);
		else this.scoreboard = null;
	}

	/**
	 * Reset gang.
	 */
	public void resetGang() {
		this.gangId = -1;
	}

	/**
	 * Has gang boolean.
	 *
	 * @return the boolean
	 */
	public boolean hasGang() {
		return this.gangId != -1;
	}

	/**
	 * Add account.
	 *
	 * @param account the account
	 */
	public void addAccount(Account<?, ?> account) {
		linkedAccounts.add(account);
	}

	/**
	 * Remove account.
	 *
	 * @param account the account
	 */
	public void removeAccount(Account<?, ?> account) {
		linkedAccounts.remove(account);
	}

	/**
	 * Gets linked accounts.
	 *
	 * @return the linked accounts
	 */
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
		return String.format("User:{data=%s,kd=%.2f,balance=%.2f,level=%.2f,gangId=%d}", user, getKillDeathRatio(),
		                     economy.getBalance(), level.getExperience(), gangId);
	}

}
