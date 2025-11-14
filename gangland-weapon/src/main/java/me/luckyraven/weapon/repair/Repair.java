package me.luckyraven.weapon.repair;

import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.repair.api.RepairAPI;
import me.luckyraven.weapon.repair.listener.AnvilRepairListener;
import me.luckyraven.weapon.repair.listener.CraftingRepairListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Main entry point for the weapon repair system.
 * <p>
 * Initialize this class in your main plugin to enable weapon repairs.
 */
public class Repair {

	private final JavaPlugin    plugin;
	private final RepairManager repairManager;
	private final RepairAPI     repairAPI;
	private final WeaponService weaponService;

	/**
	 * Creates a new repair system instance.
	 *
	 * @param plugin The plugin instance
	 * @param weaponService The weapon service
	 */
	public Repair(@NotNull JavaPlugin plugin, @NotNull WeaponService weaponService) {
		this.plugin        = plugin;
		this.weaponService = weaponService;
		this.repairManager = new RepairManager(plugin);
		this.repairAPI     = new RepairAPI(repairManager);
	}

	/**
	 * Initializes the repair system. Call this during plugin startup.
	 */
	public void initialize() {
		plugin.getLogger().info("Starting weapon repair system...");

		// Initialize repair manager
		repairManager.initialize();

		// Register listeners
		registerListeners();

		plugin.getLogger().info("Weapon repair system started successfully!");
	}

	/**
	 * Gets the public API for the repair system.
	 *
	 * @return The repair API
	 */
	@NotNull
	public RepairAPI getAPI() {
		return repairAPI;
	}

	/**
	 * Gets the repair manager.
	 *
	 * @return The repair manager
	 */
	@NotNull
	public RepairManager getRepairManager() {
		return repairManager;
	}

	/**
	 * Reloads the repair system configuration.
	 */
	public void reload() {
		repairManager.reload();
	}

	/**
	 * Shuts down the repair system. Call this during plugin shutdown.
	 */
	public void shutdown() {
		plugin.getLogger().info("Shutting down weapon repair system...");
		// Cleanup if needed
		plugin.getLogger().info("Weapon repair system shut down");
	}

	/**
	 * Registers all event listeners.
	 */
	private void registerListeners() {
		// Register crafting table support
		Bukkit.getPluginManager().registerEvents(
				new CraftingRepairListener(repairManager, weaponService), plugin);

		// Register anvil support
		Bukkit.getPluginManager().registerEvents(
				new AnvilRepairListener(repairManager, weaponService), plugin);

		plugin.getLogger().info("Registered repair station listeners");
	}
}