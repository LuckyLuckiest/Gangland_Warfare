package org.luckyraven.gangland.copsncrooks.detainment.inventory;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Snapshot of a player's inventory taken on jail intake. Persisted so legitimate releases (bail / bribe / sentence /
 * admin) can restore it. Stored as an opaque Base64-encoded blob produced by {@code BukkitObjectOutputStream}.
 */
@Getter
@Setter
public class SeizedInventory {

	private final UUID   playerId;
	private       String serializedContents;
	private       long   seizedAt;

	public SeizedInventory(UUID playerId, String serializedContents, long seizedAt) {
		this.playerId           = playerId;
		this.serializedContents = serializedContents;
		this.seizedAt           = seizedAt;
	}
}
