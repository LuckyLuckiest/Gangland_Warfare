package org.luckyraven.gangland.copsncrooks.npc.banker;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
public final class BankerData {

	private final UUID     id;
	private final Location spawnLocation;

	@Setter
	@Nullable
	private String displayName;

	public BankerData(UUID id, Location spawnLocation, @Nullable String displayName) {
		this.id            = id;
		this.spawnLocation = spawnLocation;
		this.displayName   = displayName;
	}

}
