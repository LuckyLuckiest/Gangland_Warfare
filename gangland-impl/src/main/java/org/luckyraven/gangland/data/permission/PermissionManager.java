package org.luckyraven.gangland.data.permission;

import lombok.CustomLog;
import lombok.Getter;
import org.luckyraven.gangland.Gangland;

import java.util.*;
import java.util.stream.Collectors;

@CustomLog
public class PermissionManager {

	@Getter
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

	/**
	 * Resolves a raw query — with or without the {@code gangland.} prefix — to its canonical form.
	 */
	public String resolve(String permission) {
		return permissionHandler.permissionRefactor(permission);
	}

	/**
	 * Returns every tracked permission whose canonical form starts with {@code gangland.<prefix>.} (or matches
	 * {@code gangland.<prefix>} exactly).
	 */
	public List<String> findByPrefix(String prefix) {
		String full   = permissionHandler.permissionRefactor(prefix);
		String dotted = full + ".";

		return permissions.stream()
				.filter(p -> p.equals(full) || p.startsWith(dotted))
				.sorted(String::compareTo)
				.toList();
	}

	/**
	 * Substring search across every tracked permission node. Case-insensitive.
	 */
	public List<String> search(String query) {
		String needle = query.toLowerCase();

		return permissions.stream()
				.filter(p -> p.toLowerCase().contains(needle))
				.sorted(String::compareTo)
				.toList();
	}

	/**
	 * Returns a sorted map of category prefix (the segment immediately after {@code gangland.}) to the count of tracked
	 * nodes under it. Permissions that don't follow the {@code gangland.<category>...} shape are bucketed under their
	 * first segment unchanged.
	 */
	public Map<String, Integer> getCategoryCounts() {
		Map<String, Integer> counts = new TreeMap<>();

		for (String permission : permissions) {
			String category = extractCategory(permission);
			counts.merge(category, 1, Integer::sum);
		}

		return new LinkedHashMap<>(counts);
	}

	private String extractCategory(String permission) {
		String[] parts = permission.split("\\.");
		if (parts.length >= 2 && parts[0].equalsIgnoreCase(Gangland.FULL_PREFIX)) return parts[1];

		return parts[0];
	}

}
