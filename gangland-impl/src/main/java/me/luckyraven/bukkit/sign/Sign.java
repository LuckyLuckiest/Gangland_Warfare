package me.luckyraven.bukkit.sign;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;

import java.time.Instant;
import java.util.Date;

@Getter
@Setter
public abstract class Sign {

	private static int ID = 0;

	protected final String signType;
	private final   int    usedId;

	private long   lastTimeUsed;
	private double x, y, z;
	private String world;

	public Sign(String signType, Location location) {
		this.signType     = signType;
		this.x            = location.getBlockX();
		this.y            = location.getBlockY();
		this.z            = location.getBlockZ();
		this.world        = location.getWorld() != null ? location.getWorld().getName() : "";
		this.lastTimeUsed = Instant.now().toEpochMilli();
		this.usedId       = ++ID;
	}

	protected static void setID(int id) {
		Sign.ID = id;
	}

	public void validate(String[] lines) {
		if (lines.length < 4) throw new IllegalArgumentException("The sign must have 4 lines!");

		String checkType = lines[0];

		if (!checkType.equalsIgnoreCase(signType)) throw new IllegalArgumentException("The header line is wrong!");
	}

	public Date getLastTimeUsedDate() {
		return new Date(lastTimeUsed);
	}

}
