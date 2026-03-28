package me.luckyraven.copsncrooks.police.spawn;

import lombok.Getter;
import org.bukkit.Location;

public class CopSpawner {

	@Getter
	private final int      id;
	private       Location location;

	public CopSpawner(int id, Location location) {
		this.id       = id;
		this.location = location.clone();
	}

	public Location getLocation() {
		return location.clone();
	}

	public void setLocation(Location location) {
		this.location = location.clone();
	}
}
