package org.luckyraven.gangland.file.configuration.gang;

import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.gangland.gang.contract.PermissionRegistryContract;

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
