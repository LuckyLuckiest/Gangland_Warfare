package me.luckyraven.data.permission;

import org.jetbrains.annotations.NotNull;

public interface PermissionHandler {

	String permissionRefactor(@NotNull String permission);

	void addPermission(@NotNull String permission);

	void removePermission(@NotNull String permission) throws IllegalAccessException;

	boolean permissionExists(@NotNull String permission);

}
