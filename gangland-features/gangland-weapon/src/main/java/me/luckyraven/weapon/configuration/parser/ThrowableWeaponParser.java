package me.luckyraven.weapon.configuration.parser;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.configuration.parser.AmmunitionSectionParser.ParsedAmmo;
import me.luckyraven.weapon.dto.AmmunitionData;
import me.luckyraven.weapon.dto.ReloadData;
import me.luckyraven.weapon.dto.ThrowableData;
import me.luckyraven.weapon.types.throwable.ThrowableType;
import me.luckyraven.weapon.types.throwable.ThrowableWeapon;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Parses the {@code Throw:} / {@code Shoot:} section of a THROWABLE weapon YAML and constructs a
 * {@link ThrowableWeapon}.
 */
public class ThrowableWeaponParser {

	private final AmmunitionSectionParser ammoParser;

	public ThrowableWeaponParser(AmmunitionManager ammunitionManager) {
		this.ammoParser = new AmmunitionSectionParser(ammunitionManager);
	}

	public ThrowableWeapon parse(FileConfiguration config, ConfigurationSection shootSection,
	                             WeaponBaseData base) throws InvalidConfigurationException {
		if (shootSection == null)
			throw new InvalidConfigurationException("Throw/Shoot section not found for throwable weapon");

		int     fuseTime        = shootSection.getInt("Fuse_Time", 60);
		double  explosionRadius = shootSection.getDouble("Explosion_Radius", 3.0);
		int     explosionDamage = shootSection.getInt("Explosion_Damage", 6);
		int     fireTicks       = shootSection.getInt("Fire_Ticks", 0);
		boolean bounces         = shootSection.getBoolean("Bounces", false);
		int     maxBounces      = shootSection.getInt("Max_Bounces", 5);
		boolean sticky          = shootSection.getBoolean("Sticky", false);
		String  entityType      = shootSection.getString("Entity_Type", "SNOWBALL");

		if (bounces && sticky)
			throw new InvalidConfigurationException("Throwable cannot have both Bounces and Sticky enabled");

		ThrowableType type          = ThrowableType.getType(shootSection.getString("Type"));
		List<String>  effects       = shootSection.getStringList("Effects");
		int           cloudDuration = shootSection.getInt("Cloud_Duration", 0);
		double        cloudRadius   = shootSection.getDouble("Cloud_Radius", 0.0);
		ItemStack displayItem = parseDisplayItem(shootSection.getConfigurationSection("Display_Item"),
		                                         base.fileName());

		if (type == ThrowableType.SMOKE && cloudDuration <= 0) {
			throw new InvalidConfigurationException(
					"Throwable '" + base.fileName() + "' has Type: SMOKE but Cloud_Duration is missing or <= 0");
		}
		if (type == ThrowableType.STUN && (effects == null || effects.isEmpty())) {
			throw new InvalidConfigurationException(
					"Throwable '" + base.fileName() + "' has Type: STUN but Effects list is empty");
		}

		ThrowableData throwableData = new ThrowableData(fuseTime, explosionRadius, explosionDamage, fireTicks,
		                                                bounces, maxBounces, sticky, entityType,
		                                                type, effects, cloudDuration, cloudRadius, displayItem);
		ParsedAmmo     parsed         = ammoParser.parse(config);
		ReloadData     reloadData     = parsed != null ? parsed.reload() : null;
		AmmunitionData ammunitionData = parsed != null ? parsed.ammo() : null;

		return new ThrowableWeapon(null, base.fileName(), base.displayName(), base.category(),
		                           base.material(), base.customModelData(), base.durability(), base.lore(),
		                           base.dropHologram(), base.deathMessages(), throwableData, reloadData,
		                           ammunitionData);
	}

	/**
	 * Parses an optional {@code Display_Item:} subsection. Returns {@code null} when absent so the caller falls back to
	 * the weapon's held material. Schema:
	 * <pre>
	 * Display_Item:
	 *   Material: TNT              # required
	 *   Custom_Model_Data: 1042    # optional
	 *   Name: "&8Smoke Grenade"    # optional, supports & color codes
	 *   Lore:                      # optional
	 *     - "&7A canister of dense smoke."
	 * </pre>
	 */
	private ItemStack parseDisplayItem(ConfigurationSection section, String fileName) {
		if (section == null) return null;

		String materialString = section.getString("Material");
		if (materialString == null) return null;

		Optional<XMaterial> xMaterialOptional = XMaterial.matchXMaterial(materialString);
		Material            material;
		if (xMaterialOptional.isPresent()) {
			material = xMaterialOptional.get().get();
		} else {
			throw new IllegalArgumentException(
					"Throwable '" + fileName + "' Display_Item.Material '" + materialString +
					"' is not a valid material");
		}
		if (material == null) return null;

		ItemBuilder builder = new ItemBuilder(new ItemStack(material));

		String name = section.getString("Name");
		if (name != null) builder.setDisplayName(name);

		List<String> lore = section.getStringList("Lore");
		if (!lore.isEmpty()) builder.setLore(lore);

		int customModelData = section.getInt("Custom_Model_Data", 0);
		if (customModelData > 0) builder.setCustomModelData(customModelData);

		return builder.build();
	}

}
