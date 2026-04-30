package org.luckyraven.gangland.copsncrooks.npc.trader;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
public final class TraderData {

	private final UUID     id;
	private final Location spawnLocation;

	@Setter
	private String shopKey;

	@Setter
	@Nullable
	private String displayName;

	@Setter
	private String traitId;

	public TraderData(UUID id, String shopKey, Location spawnLocation, @Nullable String displayName, String traitId) {
		this.id            = id;
		this.shopKey       = shopKey;
		this.spawnLocation = spawnLocation;
		this.displayName   = displayName;
		this.traitId       = traitId;
	}

}
