package me.luckyraven.data.account.gang;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.data.rank.Rank;
import org.jetbrains.annotations.Nullable;

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

	private           int    gangId;
	private           double contribution;
	private @Nullable Rank   rank;
	private           long   gangJoinDateLong;

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

	public void resetGang() {
		this.gangId = -1;
	}

	public boolean hasGang() {
		return this.gangId != -1;
	}

	public boolean hasRank() {
		return this.rank != null;
	}

	public Date getGangJoinDate() {
		return new Date(gangJoinDateLong);
	}

	/**
	 * Gang join date string format.
	 *
	 * @return the date in dd/MM/yyyy format
	 */
	public String getGangJoinDateString() {
		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
		return sdf.format(getGangJoinDate());
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

	@Override
	public String toString() {
		return String.format("Member{uuid=%s,gangId=%d,contribution=%.2f,rank=%s,gangJoin=%s}", uuid, gangId,
							 contribution, rank == null ? "NA" : rank.getName(), getGangJoinDateString());
	}

}
