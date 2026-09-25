package org.luckyraven.gangland.database.repositories;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.permission.Permission;
import org.luckyraven.gangland.data.plugin.PluginData;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Pins T-54 (W55, P1): with the gang module deployed, {@code PermissionRepository} used to live in core
 * ({@code org.luckyraven.gangland.database.repositories.plugin}) and get scanned/registered unconditionally by
 * {@code DatabaseConfig}, even though its only caller ({@code RankManager.initialize()},
 * {@code repositoryRegistry.getRepository(Permission.class)}) moved to the gang module. Without the module,
 * nothing ever called {@code setDataSupplier} on it, so every autosave/reload logged
 * {@code "No data supplier set for repository: PermissionRepository"} for a repository the core has no use for
 * on its own.
 *
 * <p>Fix: {@code PermissionRepository}/{@code PermissionTable} moved into the gang module's own database
 * package ({@code org.luckyraven.gangland.gang.database.repositories.permission}/{@code .tables.permission}),
 * scanned only by the module loader's own {@code repositoryPackage} pass
 * ({@code GangModule.REPOSITORY_PACKAGE}) — a gang-less server's core scan of
 * {@code org.luckyraven.gangland.database.repositories} (this class's own package, run by
 * {@code DatabaseConfig.ganglandDatabase()}) no longer finds it at all, so there is no orphan repository left to
 * warn about. The {@code Permission} entity itself stays in {@code gangland-core} (table name {@code permission}
 * unchanged; no migration). This test scans the real core package against the real classpath — no mock of
 * {@code PermissionRepository} itself, since after the fix it is not on gangland-impl's classpath at all — and
 * doesn't name the class it expects to be missing, so it stays valid regardless of where the class moved to.
 */
@DisplayName("core repository package scan (T-54)")
class CoreRepositoryScanTest {

	@Test
	@DisplayName("scanning org.luckyraven.gangland.database.repositories no longer registers a Permission repository")
	void coreScan_doesNotRegisterPermissionRepository() {
		RepositoryRegistry registry = new RepositoryRegistry(mock(JavaPlugin.class), mock(DatabaseHandler.class),
		                                                      mock(DatabaseBackend.class));

		registry.scanAndRegisterRepositories("org.luckyraven.gangland.database.repositories",
		                                     getClass().getClassLoader());

		assertFalse(registry.hasRepository(Permission.class),
		           "PermissionRepository moved to the gang module (T-54) — a gang-less server's core scan must " +
		           "not find it any more, or RankManager.setDataSupplier being the only caller left it as an " +
		           "orphan repository that logs \"No data supplier set\" on every autosave.");

		// Sanity: the scan itself still works and still finds a repository that legitimately stayed in core.
		assertTrue(registry.hasRepository(PluginData.class),
		          "the scan should still register sibling core repositories that were not moved");
	}

	@Test
	@DisplayName("every foreign key a core table declares points at a table the core scan itself registers")
	void coreScan_foreignKeysStayInsideCore() {
		RepositoryRegistry registry = new RepositoryRegistry(mock(JavaPlugin.class), mock(DatabaseHandler.class),
		                                                      mock(DatabaseBackend.class));

		registry.scanAndRegisterRepositories("org.luckyraven.gangland.database.repositories",
		                                     getClass().getClassLoader());

		Set<String> core = registry.getRegisteredTables().stream().map(Table::getName).collect(Collectors.toSet());
		List<String> dangling = registry.getRegisteredTables()
				.stream()
				.flatMap(table -> table.getAttributes()
						.values()
						.stream()
						.filter(column -> column.getAssociatedTable() != null
						                  && !core.contains(column.getAssociatedTable().getName()))
						.map(column -> table.getName() + "." + column.getName() + " -> "
						               + column.getAssociatedTable().getName()))
				.toList();

		assertEquals(List.of(), dangling,
		             "a core FK into a module-owned table never resolves without that module: the table sort warns "
		             + "\"Possible circular dependency\" every boot and MySQL refuses the CREATE TABLE outright");
	}
}
