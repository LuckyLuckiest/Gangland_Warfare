package me.luckyraven.data.permission;

import com.google.common.base.Preconditions;
import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;

public class PermissionWorker implements PermissionHandler {

	private final String indicator;

	public PermissionWorker(String indicator) {
		this.indicator = indicator;
	}

	@Override
	public String permissionRefactor(@NotNull String permission) {
		Preconditions.checkNotNull(permission, "Permission string can't be null!");
		if (permission.isEmpty()) return permission;

		return (!permission.startsWith(this.indicator) ? this.indicator + "." : "") + permission;
	}

	@Override
	public void addPermission(@NotNull String permission) {
		Preconditions.checkNotNull(permission, "Permission string can't be null!");
		if (permission.isEmpty()) return;

		Permission    perm          = new Permission(permissionRefactor(permission));
		PluginManager pluginManager = Bukkit.getPluginManager();

		if (!permissionExists(perm.getName())) pluginManager.addPermission(perm);
	}

	@Override
	public void removePermission(@NotNull String permission) throws IllegalAccessException {
		Preconditions.checkNotNull(permission, "Permission string can't be null!");
		if (permission.isEmpty()) return;

		// not moral to remove other plugins permissions
		if (!permission.startsWith(this.indicator)) throw new IllegalAccessException(
				"Not allowed to access other plugins registered permission.");

		PluginManager pluginManager = Bukkit.getPluginManager();

		pluginManager.removePermission(permission);
	}

	@Override
	public boolean permissionExists(@NotNull String permission) {
		return Bukkit.getPluginManager().getPermissions().stream().map(Permission::getName).toList().contains(
				permission);
	}

}
