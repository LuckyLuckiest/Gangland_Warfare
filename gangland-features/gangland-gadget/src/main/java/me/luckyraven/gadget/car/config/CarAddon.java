package me.luckyraven.gadget.car.config;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import me.luckyraven.exception.PluginException;
import me.luckyraven.gadget.car.Car;
import me.luckyraven.gadget.car.CarManager;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@CustomLog
public class CarAddon extends CarManager {

	private final Consumer<String> permissionRegistrar;
	private final FileManager      fileManager;

	public CarAddon(Consumer<String> permissionRegistrar, FileManager fileManager) {
		this.permissionRegistrar = permissionRegistrar;
		this.fileManager         = fileManager;
	}

	public void initialize() {
		FileConfiguration fileConfiguration;
		try {
			String fileName = "cars";

			fileManager.checkFileLoaded(fileName);

			FileHandler file = Objects.requireNonNull(fileManager.getFile(fileName));
			fileConfiguration = file.getFileConfiguration();
		} catch (IOException exception) {
			throw new PluginException(exception);
		}

		loadCars(fileConfiguration);
	}

	private void loadCars(FileConfiguration config) {
		List<String> loaded = new ArrayList<>();

		for (String key : config.getKeys(false)) {
			ConfigurationSection section = config.getConfigurationSection(key);
			if (section == null) continue;

			String materialString = section.getString("Material");
			String displayName    = section.getString("Display_Name");

			if (materialString == null || displayName == null) {
				log.warn("Car '{}' is missing Material or Display_Name - skipped.", key);
				continue;
			}

			Material material = XMaterial.matchXMaterial(materialString).orElse(XMaterial.MINECART).get();

			if (material == null) {
				log.warn("Car '{}' has invalid Material '{}' - skipped.", key, materialString);
				continue;
			}

			String       permission      = section.getString("Permission");
			int          customModelData = section.getInt("Custom_Model_Data", 0);
			boolean      dropOnDeath     = section.getBoolean("Drop_On_Death", false);
			boolean      droppable       = section.getBoolean("Droppable", true);
			List<String> lore            = section.getStringList("Lore");

			// Vehicle section
			ConfigurationSection vehicleSection = section.getConfigurationSection("Vehicle");

			double maxSpeed     = 0.8;
			double acceleration = 0.04;
			double deceleration = 0.02;
			double turnSpeed    = 4.0;
			double maxHealth    = 100.0;

			if (vehicleSection != null) {
				maxSpeed     = vehicleSection.getDouble("Max_Speed", 0.8);
				acceleration = vehicleSection.getDouble("Acceleration", 0.04);
				deceleration = vehicleSection.getDouble("Deceleration", 0.02);
				turnSpeed    = vehicleSection.getDouble("Turn_Speed", 4.0);
				maxHealth    = vehicleSection.getDouble("Max_Health", 100.0);
			}

			// Fuel section
			ConfigurationSection fuelSection = section.getConfigurationSection("Fuel");

			boolean fuelEnabled = false;
			String  fuelKey     = "";
			int     maxFuel     = 0;

			if (fuelSection != null) {
				fuelEnabled = fuelSection.getBoolean("Enabled", false);
				fuelKey     = fuelSection.getString("Fuel_Key", "");
				maxFuel     = fuelSection.getInt("Max_Fuel", 0);
			}

			// Repair section
			ConfigurationSection repairSection = section.getConfigurationSection("Repair");

			int maxDurability = 500;

			if (repairSection != null) {
				maxDurability = repairSection.getInt("Max_Durability", 500);
			}

			Car car = Car.builder()
			             .carId(key)
			             .displayName(displayName)
			             .itemMaterial(material)
			             .customModelData(customModelData)
			             .lore(lore.isEmpty() ? null : lore)
			             .permission(permission)
			             .maxSpeed(maxSpeed)
			             .acceleration(acceleration)
			             .deceleration(deceleration)
			             .turnSpeed(turnSpeed)
			             .maxHealth(maxHealth)
			             .fuelEnabled(fuelEnabled)
			             .fuelKey(fuelKey)
			             .maxFuel(maxFuel)
			             .maxDurability(maxDurability)
			             .dropOnDeath(dropOnDeath)
			             .droppable(droppable)
			             .build();

			register(key, car);
			if (permission != null && !permission.isEmpty()) {
				permissionRegistrar.accept(permission);
			}
			loaded.add(key);
		}

		log.info("Loaded the following cars:");
		log.info(loaded);
	}
}
