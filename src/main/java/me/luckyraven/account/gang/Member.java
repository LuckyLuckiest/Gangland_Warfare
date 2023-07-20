package me.luckyraven.account.gang;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.rank.Rank;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

@Getter
@Setter
public class Member {

	private final UUID uuid;

	private int    gangId;
	private double contribution;
	private Rank   rank;
	private long   gangJoinDate;

	public Member(UUID uuid) {
		this.uuid = uuid;
		this.gangId = -1;
		this.contribution = 0D;
	}

	public String gangJoinDate() {
		Date             date = new Date(gangJoinDate);
		SimpleDateFormat sdf  = new SimpleDateFormat("dd/MM/yyyy");
		return sdf.format(date);
	}

	public void increaseContribution(double amount) {
		this.contribution += amount;
	}

	public void decreaseContribution(double amount) {
		this.contribution -= amount;
	}

}
