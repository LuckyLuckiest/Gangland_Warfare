package me.luckyraven.item.configuration;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.exception.PluginException;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.util.item.wearable.Wearable;
import me.luckyraven.util.item.wearable.WearableTrait;
import me.luckyraven.weapon.wearable.WearableService;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.util.*;

/**
 * Loads and registers {@link Wearable} armor configurations from {@code wearables.yml}.
 *
 * <p>Example config entry:
 * <pre>
 * police_vest:
 *   Permission: "wearables.police_vest"
 *   Material: IRON_CHESTPLATE
 *   Name: "&7Police Vest"
 *   Drop_On_Death: true
 *   Droppable: true
 *   Base_Damage_Reduction: 0.12
 *   Leather_Color: ""          # hex color e.g. "#8B4513", only for leather armor
 *   Lore:
 *     - "&8Standard issue body armor"
 *   Traits:
 *     REINFORCED: 2
 *     BULLETPROOF: 1
 * </pre>
 */
@CustomLog
public class WearableAddon extends WearableService {

	private final PermissionManager permissionManager;
	private final FileManager       fileManager;

	public WearableAddon(PermissionManager permissionManager, FileManager fileManager) {
		this.permissionManager = permissionManager;
		this.fileManager       = fileManager;
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

	public void initialize() {
		FileConfiguration fileConfiguration;
		try {
			String fileName = "wearables";

			fileManager.checkFileLoaded(fileName);

			FileHandler file = Objects.requireNonNull(fileManager.getFile(fileName));
			fileConfiguration = file.getFileConfiguration();
		} catch (IOException exception) {
			throw new PluginException(exception);
		}

		loadWearables(fileConfiguration);
	}

	private void loadWearables(FileConfiguration config) {
		List<String> loaded = new ArrayList<>();

		for (String key : config.getKeys(false)) {
			ConfigurationSection section = config.getConfigurationSection(key);
			if (section == null) continue;

			String materialString = section.getString("Material");
			String name           = section.getString("Name");

			if (materialString == null || name == null) {
				log.warn("Wearable '" + key + "' is missing Material or Name - skipped.");
				continue;
			}

			Material material = XMaterial.matchXMaterial(materialString).orElse(XMaterial.BARRIER).get();

			if (material == null || !Wearable.isArmorMaterial(material)) {
				log.warn("Wearable '{}' has invalid/non-armor Material '{}' - skipped.", key, materialString);
				continue;
			}

			String       permission          = section.getString("Permission");
			boolean      dropOnDeath         = section.getBoolean("Drop_On_Death", true);
			boolean      droppable           = section.getBoolean("Droppable", true);
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

			Wearable wearable = Wearable.builder()
										.permission(permission)
										.material(material)
										.name(name)
										.dropOnDeath(dropOnDeath)
										.droppable(droppable)
										.lore(lore.isEmpty() ? null : lore)
										.wearableKey(key)
										.baseDamageReduction(Math.min(Math.max(baseDamageReduction, 0.0), 1.0))
										.traits(traits)
										.leatherColor(leatherColor)
										.temporary(false)
										.build();

			register(key, wearable);
			if (permission != null && !permission.isEmpty()) {
				permissionManager.addPermission(permission);
			}
			loaded.add(key);
		}

		log.info("Loaded the following wearables:");
		log.info(loaded);
	}

}
