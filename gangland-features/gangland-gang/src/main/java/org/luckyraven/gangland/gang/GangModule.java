package org.luckyraven.gangland.gang;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.vault.permission.VaultPermissionBridge;
import org.luckyraven.keystone.module.KeystoneModule;
import org.luckyraven.keystone.module.ModuleContext;
import org.luckyraven.keystone.module.ModuleRegistrar;
import org.luckyraven.keystone.vault.permission.VaultOfflinePermissionService;

/**
 * Entry point of the gang module ({@code module.yml} {@code Main}). Declares what the module contributes; the
 * host runs the scans and folds {@link GangConfig} into its phased bean pipeline.
 *
 * <ul>
 *     <li>{@link GangConfig} - the module-owned contract beans (GangMessageContract, GangPermissionBridgeContract,
 *     GangSettingsContract, GangAllianceRepositoryContract's own binding, MemberRepositoryContract's own binding)
 *     plus the {@code @Bean}-produced {@code GangMembershipInstaller} whose {@code @PostConstruct} wires the
 *     core-owned {@code GangMembership} holder (T-53, W55: not registered as its own {@code @Configuration}
 *     class — see its javadoc for why that broke boot with the module deployed).</li>
 *     <li>{@code gang.command.sub} - the {@code /glw gang} and {@code /glw rank} command trees (31 commands).</li>
 *     <li>{@code gang.listener} - {@code GangMembersDamageListener}.</li>
 *     <li>{@code gang.database} - the gang/rank/member repositories and their tables, scanned through the module
 *     loader.</li>
 * </ul>
 */
@CustomLog
public final class GangModule implements KeystoneModule {

	public static final String LISTENER_PACKAGE      = "org.luckyraven.gangland.listener.gang";
	public static final String GANG_COMMAND_PACKAGE   = "org.luckyraven.gangland.command.sub.gang";
	public static final String RANK_COMMAND_PACKAGE   = "org.luckyraven.gangland.command.sub.rank";
	public static final String REPOSITORY_PACKAGE     = "org.luckyraven.gangland.gang.database";

	@Override
	public void configure(ModuleRegistrar registrar) {
		registrar.configuration(GangConfig.class)
		         .listenerPackage(LISTENER_PACKAGE)
		         .commandPackage(GANG_COMMAND_PACKAGE)
		         .commandPackage(RANK_COMMAND_PACKAGE)
		         .repositoryPackage(REPOSITORY_PACKAGE);
	}

	@Override
	public void onEnabled(ModuleContext context) {
		linkVaultPermissions(context.host());

		log.info("Gang module {} enabled", context.module().descriptor().version());
	}

	@Override
	public void onDisabled() {
		if (VaultPermissionBridge.isEnabled()) {
			VaultPermissionBridge.set(null);
		}

		log.debug("Gang module disabled");
	}

	/**
	 * Moved from {@code Gangland.dependencyHandler()}'s {@code vaultPermissions} soft dependency (WS5 G2 step 16):
	 * {@link VaultPermissionBridge} is now module-owned, so impl can no longer resolve/hold it. {@code fromServices}
	 * is itself null-safe (no Vault permission provider registered → {@code null}), so this only logs when a link
	 * actually happens.
	 */
	private void linkVaultPermissions(JavaPlugin host) {
		// Keystone's offline-capable service owns the Vault plumbing (async dispatch for offline targets,
		// protected default group); the domain bridge stays a thin facade over it.
		VaultOfflinePermissionService service = VaultOfflinePermissionService.fromServices(host);

		if (service == null) {
			return;
		}

		VaultPermissionBridge.set(service);
		log.info("Linked Vault permissions");
	}
}
