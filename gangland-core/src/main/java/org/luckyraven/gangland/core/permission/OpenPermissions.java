package org.luckyraven.gangland.core.permission;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.luckyraven.keystone.permission.PermissionManager;

import java.util.function.Consumer;

/**
 * Registers permission nodes that every player holds (Bukkit {@code PermissionDefault.TRUE}), for nodes whose names
 * only exist at runtime (item ids, waypoint ids) and so cannot be declared in {@code plugin.yml} (T-825, T-826).
 */
public final class OpenPermissions {

	private OpenPermissions() {
	}

	public static void register(PermissionManager permissionManager, String node) {
		register(permissionManager, Bukkit.getServer() == null ? null : Bukkit.getPluginManager(), node);
	}

	static void register(PermissionManager permissionManager, PluginManager plugins, String node) {
		permissionManager.addPermission(node);
	}

	public static Consumer<String> registrar(PermissionManager permissionManager) {
		return node -> register(permissionManager, node);
	}

}
