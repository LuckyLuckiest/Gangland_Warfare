package me.luckyraven.account.gang;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.rank.Rank;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * Handles all users registered data, either online or offline.
 */
@Getter
@Setter
public class Member {

	private final UUID uuid;

	private int    gangId;
	private double contribution;
	private Rank   rank;
	private long   gangJoinDate;

	/**
	 * Instantiates a new Member.
	 *
	 * @param uuid the uuid
	 */
	public Member(UUID uuid) {
		this.uuid = uuid;
		this.gangId = -1;
		this.contribution = 0D;
	}

	/**
	 * Gang join date string format.
	 *
	 * @return the date in dd/MM/yyyy format
	 */
	public String gangJoinDate() {
		Date             date = new Date(gangJoinDate);
		SimpleDateFormat sdf  = new SimpleDateFormat("dd/MM/yyyy");
		return sdf.format(date);
	}

	/**
	 * Increase contribution.
	 *
	 * @param amount the amount to add
	 */
	public void increaseContribution(double amount) {
		this.contribution += amount;
	}

	/**
	 * Decrease contribution.
	 *
	 * @param amount the amount to decrease
	 */
	public void decreaseContribution(double amount) {
		this.contribution -= amount;
	}

}
