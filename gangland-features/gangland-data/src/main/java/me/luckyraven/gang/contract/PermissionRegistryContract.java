package me.luckyraven.gang.contract;

/**
 * Read-side contract over impl's {@code PermissionManager} — exposed so the rank module can check whether a permission
 * node is tracked by the plugin without importing PermissionManager directly.
 */
public interface PermissionRegistryContract {

	boolean contains(String permission);
}
