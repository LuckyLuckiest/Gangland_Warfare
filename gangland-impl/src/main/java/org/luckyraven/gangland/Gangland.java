package org.luckyraven.gangland;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.ViaAPI;
import com.zaxxer.hikari.HikariConfig;
import lombok.CustomLog;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.bootstrap.GanglandContext;
import org.luckyraven.gangland.bootstrap.ShutdownSequence;
import org.luckyraven.gangland.bootstrap.ReloadPlugin;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.gangland.data.placeholder.worker.GanglandPlaceholder;
import org.luckyraven.keystone.papi.PapiExpansionAdapter;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.file.configuration.inventory.InventoryDefinitionStore;
import org.luckyraven.keystone.sound.ResourcePackTracker;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.update.UpdateNotifier;
import org.luckyraven.keystone.update.UpdateChecker;

import java.util.List;

@Getter
@CustomLog
public final class Gangland extends JavaPlugin {

	public static final String FULL_PREFIX  = GanglandApi.FULL_PREFIX;
	public static final String SHORT_PREFIX = GanglandApi.SHORT_PREFIX;

	private GanglandContext      context;
	private ReloadPlugin         reloadPlugin;
	private UpdateNotifier       updateChecker;
	private PapiExpansionAdapter papiExpansion;
	private ViaAPI<?>            viaAPI;

	@Override
	public void onLoad() {
		// disable HikariCP logs
		disableAllLogs(HikariConfig.class);
	}

	@Override
	public void onDisable() {
		// Symmetric teardown (mirrors Bartizan.java's onDisable): GanglandPlaceholder is registered as a
		// PlaceholderProvider service at bean construction (WiringConfig.ganglandPlaceholder), so a disable/enable
		// cycle must not leave a dead provider behind for an external consumer (Plaque) to resolve. First statement,
		// before the context-null early return below, so it always runs even if onEnable() never completed.
		getServer().getServicesManager().unregisterAll(this);

		// vault soft dependency economy check
		if (EconomyHandler.getVaultEconomy() != null) {
			EconomyHandler.setVaultEconomy(null);
		}

		// Vault permissions teardown moved to GangModule.onDisabled() (WS5 G2 step 16) — VaultPermissionBridge is
		// module-owned; impl-side context.disableModules() (inside ShutdownSequence below) runs the module's
		// onDisabled() before this method returns.

		// uninstall the resource-pack tracker so custom-sound gating doesn't outlive the plugin
		ResourcePackTracker tracker = ResourcePackTracker.active();
		if (tracker != null) {
			tracker.clear();
			ResourcePackTracker.install(null);
		}

		// onDisable() also runs when onEnable() never completed
		if (context == null) return;

		// Every stage (bean shutdown, module disable, final save, connection close, backend disconnect) is isolated
		// inside ShutdownSequence: one throwing onShutdown() must never skip the final save or the DB close.
		new ShutdownSequence(context).run();
	}

	@Override
	public void onEnable() {
		// Create the root DI context and drive the full phased bean pipeline (KERNEL → FILE → DATABASE →
		// CONFIG → LIFECYCLE → LISTENER → COMMAND). KernelConfig produces every bootstrap-critical singleton;
		// all managers, services, and addons are wired by @Configuration classes under org.luckyraven.gangland.config.
		this.context = new GanglandContext(this);
		context.bootstrap();

		reloadPlugin = new ReloadPlugin(context);

		// checks for dependencies
		dependencyHandler();

		// initialize bstats
		bStats();

		// check for new updates
		updateCheckerInitializer();
	}

	/**
	 * Uses bStats to create statistical metrics for development purposes.
	 */
	private void bStats() {
		int     pluginId = 21012;
		Metrics metrics  = new Metrics(this, pluginId);

		// number of inventories loaded
		metrics.addCustomChart(new SingleLineChart("number_of_inventories",
		                                           () -> context.get(InventoryDefinitionStore.class).size()));

		// number_of_ranks / number_of_gangs charts dropped (WS5 G2 step 16): RankManager/GangManager are
		// module-owned now and impl can't name them; not worth a new cross-boundary seam for two bStats counters.

		// number of waypoints
		metrics.addCustomChart(
				new SingleLineChart("number_of_waypoints", () -> context.get(WaypointManager.class).size()));
	}

	/**
	 * Removes all the logs of the specified class.
	 *
	 * @param clazz the class that contains the logs
	 */
	private void disableAllLogs(@NotNull Class<?> clazz) {
		String path = clazz.getPackageName();

		Configurator.setLevel(path, Level.ERROR);
	}

	/**
	 * Initializes the dependency handler by checking for each required and soft dependency of the plugin.
	 * </b>
	 * The plugin gets initialized based on the dependencies provided.
	 */
	private void dependencyHandler() {
		// required dependencies
		Dependency nbtApi = new Dependency("NBTAPI", Dependency.Type.REQUIRED);
		nbtApi.validate(null);

		// Citizens is a soft dependency (0.9.0, T-M2): NPC-owning modules degrade with a readable fault
		// (NpcSupport.FAULT_CITIZENS_MISSING) instead of the whole plugin refusing to enable.
		Dependency citizens = new Dependency("Citizens", Dependency.Type.SOFT);
		citizens.validate(null);

		// soft dependencies
		Dependency placeholderApi = new Dependency("PlaceholderAPI", Dependency.Type.SOFT);
		placeholderApi.validate(() -> {
			GanglandPlaceholder placeholder = context.get(GanglandPlaceholder.class);
			this.papiExpansion = new PapiExpansionAdapter(this, FULL_PREFIX, placeholder);
			this.papiExpansion.register();
		});

		// Two separate soft dependencies both named "Vault" (economy hook, permission hook) used to share the
		// generic "Found Vault, linking..." / "Linked Vault" log text, so the pair printed twice per boot with no
		// way to tell which hook either line belonged to (T-17). The label parameter keeps the plugin lookup on
		// "Vault" for both but makes the two log lines distinct.
		Dependency vault = new Dependency("Vault", "Vault economy", Dependency.Type.SOFT);
		vault.validate(() -> {
			RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);

			if (rsp == null) return;

			// set the vault economy
			EconomyHandler.setVaultEconomy(rsp.getProvider());
		});

		// Vault permissions linking moved to GangModule.onEnabled() (WS5 G2 step 16) — VaultPermissionBridge is
		// module-owned; the module runs its own fromServices/set(...) once the module loader enables it.

		Dependency viaVersion = new Dependency("ViaVersion", Dependency.Type.SOFT);
		viaVersion.validate(() -> this.viaAPI = Via.getAPI());
	}

	/**
	 * Initializes the update checker timer, which checks if there was a new update for the plugin published.
	 */
	private void updateCheckerInitializer() {
		if (!Settings.isUpdaterEnabled()) {
			return;
		}

		// there needs to be checks every 6 hours
		// give an option if there was an update
		int hours      = 6;
		int resourceId = 131157;

		// Keystone's checker fetches/compares/downloads and registers the check permission itself; Keystone's
		// UpdateNotifier wraps it with the periodic operator notification + auto-update policy, seamed on
		// Gangland's settings toggle and command-message styling.
		UpdateChecker checker = new UpdateChecker(this, context.get(PermissionManager.class), FULL_PREFIX, resourceId);
		this.updateChecker = new UpdateNotifier(this, checker, hours * 60 * 60L,
		                                        Settings::isUpdaterAutoUpdate, GanglandChatUtil::commandMessage);

		// the tasks and timer should be async, so there is no load on the main server thread
		updateChecker.start();
	}

	/**
	 * A class helper that initializes this plugin and links it with other plugins.
	 */
	private class Dependency {

		private final Type   type;
		private final String name;
		private final String label;

		public Dependency(String name, Type type) {
			this(name, name, type);
		}

		/**
		 * @param name  the Bukkit plugin name looked up via {@code getPluginManager().getPlugin(name)} - must match
		 *              the dependency's actual plugin name.
		 * @param label the text used in the "Found {}, linking..." / "Linked {}" log lines - defaults to
		 *              {@code name}, but can be given a more specific value when two {@code Dependency} instances
		 *              share the same plugin name (e.g. Vault's economy and permission hooks, T-17) so their log
		 *              lines stay distinguishable.
		 */
		public Dependency(String name, String label, Type type) {
			this.name = name;
			this.label = label;
			this.type = type;
		}

		public void validate(@Nullable Runnable runnable) {
			// A present-but-disabled plugin (e.g. Citizens failed its own enable) must not be treated as linked
			// (T-M2) — getPlugin(name) != null alone accepts that case.
			Plugin p = Bukkit.getPluginManager().getPlugin(name);
			if (p != null && p.isEnabled()) {
				if (type == Type.SOFT) log.info("Found {}, linking...", label);
				if (runnable != null) runnable.run();

				log.info("Linked {}", label);
				return;
			}

			if (type != Type.REQUIRED) return;

			log.error("{} is a required dependency!", label);
			getPluginLoader().disablePlugin(Gangland.this);
		}

		enum Type {
			REQUIRED,
			SOFT
		}
	}

}
