package me.luckyraven.data.permission;

import lombok.CustomLog;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@CustomLog
public class PermissionManager {

	private final PermissionHandler permissionHandler;
	private final Set<String>       permissions;

	public PermissionManager(PermissionHandler permissionHandler) {
		this.permissionHandler = permissionHandler;
		this.permissions       = new HashSet<>();
	}

	public void addAllPermissions(Set<String> permissions) {
		permissions.forEach(this::addPermission);
	}

	public void addPermission(String permission) {
		if (permission == null || permission.isEmpty()) return;

		permissionHandler.addPermission(permission);
		permissions.add(permissionHandler.permissionRefactor(permission));
	}

	public void removePermission(String permission, boolean removeFromHandler) {
		if (permission.isEmpty()) return;

		if (removeFromHandler) try {
			permissionHandler.removePermission(permission);
		} catch (IllegalAccessException exception) {
			log.warn(exception.getMessage(), exception);
		}

		permissions.remove(permission);
	}

	public boolean contains(String permission) {
		return permissions.contains(permission);
	}

	public Set<String> getPermissions() {
		return permissions.stream().sorted(String::compareTo).collect(Collectors.toCollection(LinkedHashSet::new));
	}

	public int size() {
		return permissions.size();
	}

}
