package me.luckyraven.data.permission;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class PermissionManager {

	private final JavaPlugin        plugin;
	private final PermissionHandler permissionHandler;
	private final Set<String>       permissions;

	public PermissionManager(JavaPlugin plugin, PermissionHandler permissionHandler) {
		this.plugin            = plugin;
		this.permissionHandler = permissionHandler;
		this.permissions       = new HashSet<>();
	}

	public void addAllPermissions(Set<String> permissions) {
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

	public Set<String> getPermissions() {
		return new HashSet<>(permissions).stream()
										 .sorted(String::compareTo)
										 .collect(Collectors.toCollection(LinkedHashSet::new));
	}

	public int size() {
		return permissions.size();
	}

}
