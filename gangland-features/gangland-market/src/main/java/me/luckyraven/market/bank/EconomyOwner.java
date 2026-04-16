package me.luckyraven.market.bank;

import org.bukkit.OfflinePlayer;

/**
 * Implemented by anything that owns an {@link EconomyHandler} and can expose a Bukkit {@link OfflinePlayer} for Vault
 * synchronization. Kept in the market module to break the circular dependency that would otherwise exist between
 * {@code EconomyHandler} and {@code User} in {@code gangland-impl}.
 */
public interface EconomyOwner {

	OfflinePlayer getUser();
}
