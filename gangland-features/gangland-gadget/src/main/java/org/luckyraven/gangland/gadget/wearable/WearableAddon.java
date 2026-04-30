package org.luckyraven.gangland.gadget.wearable;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.Placeholder;
import org.luckyraven.gangland.core.configuration.SoundConfiguration;
import org.luckyraven.gangland.exception.PluginException;
import org.luckyraven.gangland.item.wearable.Wearable;
import org.luckyraven.gangland.item.wearable.WearableTrait;
import org.luckyraven.gangland.persistence.FileHandler;
import org.luckyraven.gangland.persistence.FileInitializer;
import org.luckyraven.gangland.persistence.FileManager;
import org.luckyraven.gangland.weapon.wearable.WearableService;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

@CustomLog
public class WearableAddon extends WearableService implements FileInitializer {

	private final Consumer<String> permissionRegistrar;
	private final FileHandler      fileHandler;

	/**
	 * Placeholder resolver injected by the impl side; threaded into every built {@link Wearable} so its display name
	 * and lore resolve {@code %gangland_*%} tokens at item-build time.
	 */
	@Nullable
	private final Placeholder placeholder;

	public WearableAddon(Consumer<String> permissionRegistrar, FileManager fileManager,
	                     @Nullable Placeholder placeholder) {
		this.permissionRegistrar = permissionRegistrar;
		this.placeholder         = placeholder;

		try {
			String fileName = "wearables";

			fileManager.checkFileLoaded(fileName);

			this.fileHandler = Objects.requireNonNull(fileManager.getFile(fileName));
		} catch (IOException exception) {
			throw new PluginException(exception);
		}
	}

	/**
	 * Reads a sound sub-section at {@code path} inside {@code parent} and constructs a {@link SoundConfiguration}.
	 * Returns {@code null} if the sub-section or its {@code Sound} key is missing.
	 */
	@Nullable
	private static SoundConfiguration parseSoundConfig(ConfigurationSection parent, String path,
	                                                   SoundConfiguration.SoundType type) {
		ConfigurationSection sub = parent.getConfigurationSection(path);
		if (sub == null) return null;
		String sound = sub.getString("Sound");
		if (sound == null || sound.isEmpty()) return null;
		float volume = (float) sub.getDouble("Volume", 1.0);
		float pitch  = (float) sub.getDouble("Pitch", 1.0);
		return new SoundConfiguration(type, sound, volume, pitch);
	}

	/**
	 * Parses a CSS-style hex color string ({@code "#RRGGBB"} or {@code "RRGGBB"}) into a Bukkit {@link Color}. Returns
	 * {@code null} if parsing fails.
	 */
	private static Color parseHexColor(String hex) {
		try {
			String clean = hex.startsWith("#") ? hex.substring(1) : hex;
			int    rgb   = Integer.parseUnsignedInt(clean, 16);
			return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

	@Override
	public void initialize() {
		loadWearables(fileHandler.getFileConfiguration());
	}

	private void loadWearables(FileConfiguration config) {
		List<String> loaded = new ArrayList<>();

		for (String key : config.getKeys(false)) {
			ConfigurationSection section = config.getConfigurationSection(key);
			if (section == null) continue;

			String materialString = section.getString("Material");
			String name           = section.getString("Name");

			if (materialString == null || name == null) {
				log.warn("Wearable '{}' is missing Material or Name - skipped.", key);
				continue;
			}

			Material material = XMaterial.matchXMaterial(materialString).orElse(XMaterial.BARRIER).get();

			if (material == null || !Wearable.isArmorMaterial(material)) {
				log.warn("Wearable '{}' has invalid/non-armor Material '{}' - skipped.", key, materialString);
				continue;
			}

			int          customModelData     = section.getInt("Custom_Model_Data", 0);
			double       baseDamageReduction = section.getDouble("Base_Damage_Reduction", 0.0);
			List<String> lore                = section.getStringList("Lore");

			// Leather color (optional - only meaningful for leather armor)
			Color  leatherColor      = null;
			String leatherColorValue = section.getString("Leather_Color");
			if (leatherColorValue != null && !leatherColorValue.isEmpty() && Wearable.isLeatherArmor(material)) {
				leatherColor = parseHexColor(leatherColorValue);
			}

			// Traits
			Map<WearableTrait, Integer> traits        = new HashMap<>();
			ConfigurationSection        traitsSection = section.getConfigurationSection("Traits");
			if (traitsSection != null) {
				for (String traitKey : traitsSection.getKeys(false)) {
					WearableTrait trait = WearableTrait.fromKey(traitKey);
					if (trait == null) {
						log.warn("Unknown trait '{}' in wearable '{}' - ignored.", traitKey, key);
						continue;
					}
					int level = Math.min(traitsSection.getInt(traitKey, 1), trait.getMaxLevel());
					traits.put(trait, level);
				}
			}

			// Jetpack section (optional)
			String             fuelKey             = null;
			int                fuelConsumptionRate = 0;
			double             ascendPower         = 0;
			double             glideDescentRate    = 0;
			double             maxSpeedY           = 0;
			int                maxFuel             = 0;
			SoundConfiguration thrustDefaultSound  = null;
			SoundConfiguration thrustCustomSound   = null;
			SoundConfiguration glideDefaultSound   = null;
			SoundConfiguration glideCustomSound    = null;

			ConfigurationSection jetpackSection = section.getConfigurationSection("Jetpack");
			if (jetpackSection != null) {
				fuelKey             = jetpackSection.getString("Fuel_Key", "");
				fuelConsumptionRate = jetpackSection.getInt("Fuel_Consumption_Rate", 2);
				ascendPower         = jetpackSection.getDouble("Ascend_Power", 0.35);
				glideDescentRate    = jetpackSection.getDouble("Glide_Descent_Rate", -0.05);
				maxSpeedY           = jetpackSection.getDouble("Max_Speed_Y", 0.8);
				maxFuel             = jetpackSection.getInt("Max_Fuel", 3600);

				ConfigurationSection soundSection = jetpackSection.getConfigurationSection("Sound");
				if (soundSection != null) {
					thrustDefaultSound = parseSoundConfig(soundSection, "Thrust.Default_Sound",
					                                      SoundConfiguration.SoundType.VANILLA);
					thrustCustomSound  = parseSoundConfig(soundSection, "Thrust.Custom_Sound",
					                                      SoundConfiguration.SoundType.CUSTOM);
					glideDefaultSound  = parseSoundConfig(soundSection, "Glide.Default_Sound",
					                                      SoundConfiguration.SoundType.VANILLA);
					glideCustomSound   = parseSoundConfig(soundSection, "Glide.Custom_Sound",
					                                      SoundConfiguration.SoundType.CUSTOM);
				}
			}

			Wearable wearable = Wearable.builder()
			                            .material(material)
			                            .customModelData(customModelData)
			                            .name(name)
			                            .lore(lore.isEmpty() ? null : lore)
			                            .wearableKey(key)
			                            .baseDamageReduction(Math.clamp(baseDamageReduction, 0.0, 1.0))
			                            .traits(traits)
			                            .leatherColor(leatherColor)
			                            .temporary(false)
			                            .fuelKey(fuelKey)
			                            .fuelConsumptionRate(fuelConsumptionRate)
			                            .ascendPower(ascendPower)
			                            .glideDescentRate(glideDescentRate)
			                            .maxSpeedY(maxSpeedY)
			                            .maxFuel(maxFuel)
			                            .thrustDefaultSound(thrustDefaultSound)
			                            .thrustCustomSound(thrustCustomSound)
			                            .glideDefaultSound(glideDefaultSound)
			                            .glideCustomSound(glideCustomSound)
			                            .placeholder(placeholder)
			                            .build();

			register(key, wearable);
			permissionRegistrar.accept(wearable.getPermission());
			loaded.add(key);
		}

		log.debug("Loaded the following wearables:");
		log.debug(loaded);
	}

}
