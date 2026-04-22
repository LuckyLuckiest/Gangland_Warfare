package me.luckyraven.file.configuration.gang;

import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.gang.contract.PermissionRegistryContract;

/**
 * Adapter exposing impl-side {@link PermissionManager} as a {@link PermissionRegistryContract} for gang-module rank
 * logic.
 */
public final class GanglandPermissionRegistry implements PermissionRegistryContract {

	private final PermissionManager delegate;

	public GanglandPermissionRegistry(PermissionManager delegate) {
		this.delegate = delegate;
	}

	@Override
	public boolean contains(String permission) {
		return delegate.contains(permission);
	}
}
