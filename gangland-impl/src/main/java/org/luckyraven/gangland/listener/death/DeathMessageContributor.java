package org.luckyraven.gangland.listener.death;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * A module's answer to "what killed this player", for the core death-message builder. The core cannot name a
 * module's item type, so a module registers a bean implementing this contract; {@code PlayerDeathListener} pulls
 * every implementation out of the container, lazily, and uses the first non-null result.
 */
public interface DeathMessageContributor {

	@Nullable
	Resolved resolve(Player victim, Player killer);

	record Resolved(@Nullable String template, String itemName) {
	}
}
