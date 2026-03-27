package me.luckyraven.gadget.repair.config;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.repair.RepairableType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class YamlRepairConfigProvider implements RepairConfigProvider {

	private static final String HEAD = "Repair_Materials";

	private final Map<String, RepairMaterialData> repairMaterials;

	public YamlRepairConfigProvider(@NotNull FileConfiguration config) {
		this.repairMaterials = new LinkedHashMap<>();
		loadRepairMaterials(config);
	}

	@Override
	public @NotNull Map<String, RepairMaterialData> getRepairMaterials() {
		return Collections.unmodifiableMap(repairMaterials);
	}

	private void loadRepairMaterials(FileConfiguration config) {
		ConfigurationSection section = config.getConfigurationSection(HEAD);
		if (section == null) return;

		for (String id : section.getKeys(false)) {
			ConfigurationSection materialSection = section.getConfigurationSection(id);
			if (materialSection == null) continue;

			RepairMaterialData data = parseMaterialData(id, materialSection);
			if (data != null) {
				repairMaterials.put(id, data);
			}
		}
	}

	@Nullable
	private RepairMaterialData parseMaterialData(String id, ConfigurationSection section) {
		String displayName = section.getString("Display_Name");
		if (displayName == null) return null;

		Material material      = parseMaterial(section.getString("Material", "PAPER"));
		int      maxUses       = section.getInt("Uses", 1);
		int      restoreAmount = section.getInt("Restore_Amount", 0);
		double   restorePct    = section.getDouble("Restore_Percent", 0.0);
		int      modelData     = section.getInt("Custom_Model_Data", 0);

		List<String>         lore            = section.getStringList("Lore");
		List<RepairableType> compatibleTypes = parseCompatibleTypes(section.getStringList("Compatible_Types"));

		SoundConfiguration repairDefaultSound = null;
		SoundConfiguration repairCustomSound  = null;

		ConfigurationSection soundSection = section.getConfigurationSection("Sound");
		if (soundSection != null) {
			ConfigurationSection defaultSound = soundSection.getConfigurationSection("Default_Sound");
			if (defaultSound != null) {
				String sound  = Objects.requireNonNull(defaultSound.getString("Sound"));
				float  volume = (float) defaultSound.getDouble("Volume", 1.0);
				float  pitch  = (float) defaultSound.getDouble("Pitch", 1.0);
				repairDefaultSound = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, sound, volume, pitch);
			}

			ConfigurationSection customSound = soundSection.getConfigurationSection("Custom_Sound");
			if (customSound != null) {
				String sound  = Objects.requireNonNull(customSound.getString("Sound"));
				float  volume = (float) customSound.getDouble("Volume", 1.0);
				float  pitch  = (float) customSound.getDouble("Pitch", 1.0);
				repairCustomSound = new SoundConfiguration(SoundConfiguration.SoundType.CUSTOM, sound, volume, pitch);
			}
		}

		return RepairMaterialData.builder()
		                         .id(id)
		                         .displayName(displayName)
		                         .material(material)
		                         .maxUses(maxUses)
		                         .restoreAmount(restoreAmount)
		                         .restorePercent(restorePct)
		                         .repairDefaultSound(repairDefaultSound)
		                         .repairCustomSound(repairCustomSound)
		                         .lore(lore)
		                         .customModelData(modelData)
		                         .compatibleTypes(compatibleTypes)
		                         .build();
	}

	@NotNull
	private Material parseMaterial(@NotNull String name) {
		Optional<XMaterial> xMaterial = XMaterial.matchXMaterial(name.toUpperCase());
		if (xMaterial.isPresent()) {
			Material mat = xMaterial.get().get();
			if (mat != null) return mat;
		}
		return Material.PAPER;
	}

	@NotNull
	private List<RepairableType> parseCompatibleTypes(@NotNull List<String> types) {
		List<RepairableType> result = new ArrayList<>();
		for (String type : types) {
			try {
				result.add(RepairableType.valueOf(type.toUpperCase()));
			} catch (IllegalArgumentException ignored) {
			}
		}
		if (result.isEmpty()) {
			result.add(RepairableType.WEAPON);
		}
		return result;
	}
}
