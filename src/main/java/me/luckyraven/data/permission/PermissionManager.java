package me.luckyraven.data.permission;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class PermissionManager {

	private final JavaPlugin        plugin;
	private final PermissionHandler permissionHandler;
	private final List<String>      permissions;

	public PermissionManager(JavaPlugin plugin, PermissionHandler permissionHandler) {
		this.plugin = plugin;
		this.permissionHandler = permissionHandler;
		this.permissions = new ArrayList<>();
	}

	public void addAllPermissions(List<String> permissions) {
		permissions.forEach(this::addPermission);
	}

	public void addPermission(String permission) {
		if (permission.isEmpty()) return;

		permissionHandler.addPermission(permission);
		permissions.add(permissionHandler.permissionRefactor(permission));
	}

	public void removePermission(String permission, boolean removeFromHandler) {
		if (permission.isEmpty()) return;

		if (removeFromHandler) try {
			permissionHandler.removePermission(permission);
		} catch (IllegalAccessException exception) {
			plugin.getLogger().log(Level.WARNING, exception.getMessage(), exception);
		}

		permissions.remove(permission);
	}

	public boolean contains(String permission) {
		return permissions.contains(permission);
	}

	public List<String> getPermissions() {
		List<String> perms = new ArrayList<>(permissions);

		perms.sort(String::compareTo);

		return perms;
	}

}
