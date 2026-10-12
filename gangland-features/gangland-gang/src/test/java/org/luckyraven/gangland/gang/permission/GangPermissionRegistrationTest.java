package org.luckyraven.gangland.gang.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.permission.PermissionRegistryContract;
import org.luckyraven.gangland.gang.GangConfig;
import org.luckyraven.gangland.gang.contract.GangMigrationsContract;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Every {@code gangland.gang.*} node must reach Keystone's {@link PermissionManager} when the rank manager bean is
 * built, so {@code /glw permission} search, help and LuckPerms completion list them (fix round 1).
 */
@DisplayName("GangPermissions - every gangland.gang.* node is registered with the PermissionManager")
class GangPermissionRegistrationTest {

	@Test
	@DisplayName("the rankManager bean registers each ALL node exactly once with the PermissionManager")
	void rankManagerBean_registersEveryAllNode() {
		PermissionManager       permissionManager = mock(PermissionManager.class);
		RepositoryRegistry      registry          = mock(RepositoryRegistry.class);
		PermissionRegistryContract contract       = mock(PermissionRegistryContract.class);
		GangRules               rules             = mock(GangRules.class);

		new GangConfig().rankManager(registry, contract, rules, permissionManager, GangMigrationsContract.NONE);

		for (String node : GangPermissions.ALL) {
			verify(permissionManager).addPermission(node);
		}
		verify(permissionManager, times(GangPermissions.ALL.size())).addPermission(anyString());
	}

}
