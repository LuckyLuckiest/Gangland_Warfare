package me.luckyraven.data.permission;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class PermissionManager {

	private static final Logger logger = LogManager.getLogger(PermissionManager.class.getSimpleName());

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
			logger.warn(exception.getMessage(), exception);
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
