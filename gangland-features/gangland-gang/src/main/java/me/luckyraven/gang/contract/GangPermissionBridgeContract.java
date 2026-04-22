package me.luckyraven.gang.contract;

import org.bukkit.OfflinePlayer;

/**
 * Thin adapter over {@code VaultPermissionBridge} so consumers that need permission-bridge access through a DI-injected
 * bean don't have to reach into static methods directly. All methods are safe no-ops when Vault is unavailable.
 */
public interface GangPermissionBridgeContract {

	boolean isEnabled();

	boolean has(OfflinePlayer player, String node);

	void grant(OfflinePlayer player, String node);

	void revoke(OfflinePlayer player, String node);
}
