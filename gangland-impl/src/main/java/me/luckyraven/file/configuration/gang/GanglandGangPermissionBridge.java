package me.luckyraven.file.configuration.gang;

import me.luckyraven.gang.contract.GangPermissionBridgeContract;
import me.luckyraven.gang.vault.permission.VaultPermissionBridge;
import org.bukkit.OfflinePlayer;

/**
 * Routes {@link GangPermissionBridgeContract} calls through the static {@link VaultPermissionBridge} in the gang
 * module. Every call is already a safe no-op when Vault is unavailable (see VaultPermissionBridge javadoc), so this
 * adapter doesn't need extra guards.
 */
public final class GanglandGangPermissionBridge implements GangPermissionBridgeContract {

	@Override
	public boolean isEnabled() {
		return VaultPermissionBridge.isEnabled();
	}

	@Override
	public boolean has(OfflinePlayer player, String node) {
		return VaultPermissionBridge.has(player, node);
	}

	@Override
	public void grant(OfflinePlayer player, String node) {
		VaultPermissionBridge.grant(player, node);
	}

	@Override
	public void revoke(OfflinePlayer player, String node) {
		VaultPermissionBridge.revoke(player, node);
	}
}
