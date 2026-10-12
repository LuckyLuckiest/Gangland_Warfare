package org.luckyraven.gangland.core.permission;

import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.luckyraven.keystone.permission.PermissionManager;

import java.util.function.Consumer;

/**
 * Registers permission nodes that every player holds (Bukkit {@code PermissionDefault.TRUE}), for nodes whose names
 * only exist at runtime (item ids, waypoint ids) and so cannot be declared in {@code plugin.yml} (T-825, T-826).
 *
 * <p>Keystone's {@code PermissionManager.addPermission} registers with the OP default and skips any name Bukkit
 * already knows, so the open node is added to Bukkit first.
 */
public final class OpenPermissions {

	private OpenPermissions() {
	}

	public static void register(PermissionManager permissionManager, String node) {
		register(permissionManager, Bukkit.getServer() == null ? null : Bukkit.getPluginManager(), node);
	}

	static void register(PermissionManager permissionManager, PluginManager plugins, String node) {
		if (plugins != null) {
			String full = permissionManager.resolve(node);

			if (full != null && plugins.getPermission(full) == null) {
				plugins.addPermission(new Permission(full, PermissionDefault.TRUE));
			}
		}

		permissionManager.addPermission(node);
	}

	public static Consumer<String> registrar(PermissionManager permissionManager) {
		return node -> register(permissionManager, node);
	}

}
