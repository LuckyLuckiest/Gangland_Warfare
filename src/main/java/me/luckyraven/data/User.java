package me.luckyraven.data;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.data.rank.RankAttribute;
import org.bukkit.entity.Player;

/**
 * Handles all users registered data, either online or offline.
 *
 * @param <T> type of the user
 */
public class User<T> {

	private @Getter
	final   T      user;
	private @Getter
	@Setter double kills, deaths, mobKills;
	private @Getter
	@Setter double  balance;
	private @Getter
	@Setter boolean hasBank, hasGang;
	private @Getter
	@Setter int           gangId;
	private @Getter
	@Setter RankAttribute rank;

	{
		this.kills = 0;
		this.deaths = 0;
		this.mobKills = 0;
		this.balance = 0D;
		hasBank = false;
		hasGang = false;
		gangId = -1;
	}

	/**
	 * Instantiates a new Database.
	 *
	 * @param user the user
	 */
	public User(T user) {
		this.user = user;
	}

	/**
	 * Instantiates a new Database.
	 *
	 * @param user     the user
	 * @param kills    the kills
	 * @param deaths   the deaths
	 * @param mobKills the mob kills
	 * @param balance  the balance
	 * @param hasBank  the has bank
	 * @param hasGang  the has gang
	 */
	public User(T user, int kills, int deaths, int mobKills, double balance, boolean hasBank, boolean hasGang) {
		this(user);
		this.kills = kills;
		this.deaths = deaths;
		this.mobKills = mobKills;
		this.balance = balance;
		this.hasBank = hasBank;
		this.hasGang = hasGang;
	}

	/**
	 * Instantiates a new Database.
	 *
	 * @param user     the user
	 * @param kills    the kills
	 * @param deaths   the deaths
	 * @param mobKills the mob kills
	 * @param balance  the balance
	 * @param hasBank  the has bank
	 * @param hasGang  the has gang
	 * @param gangId   the gang id
	 */
	public User(T user, int kills, int deaths, int mobKills, double balance, boolean hasBank, boolean hasGang,
	            int gangId) {
		this(user, kills, deaths, mobKills, balance, hasBank, hasGang);
		this.gangId = gangId;
	}

	public void resetGang() {
		this.hasGang = false;
		this.gangId = -1;
	}

	/**
	 * Gets kills/deaths ratio of the user.
	 *
	 * @return the kd ratio
	 */
	public double getKillDeathRatio() {
		return deaths == 0 ? 0D : kills / deaths;
	}

}
