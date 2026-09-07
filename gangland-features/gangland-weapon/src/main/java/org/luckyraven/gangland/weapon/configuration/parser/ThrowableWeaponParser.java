package org.luckyraven.gangland.weapon.configuration.parser;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;
import org.luckyraven.gangland.weapon.configuration.parser.AmmunitionSectionParser.ParsedAmmo;
import org.luckyraven.gangland.weapon.dto.AmmunitionData;
import org.luckyraven.gangland.weapon.dto.ReloadData;
import org.luckyraven.gangland.weapon.dto.ThrowableData;
import org.luckyraven.gangland.weapon.types.throwable.ThrowableType;
import org.luckyraven.gangland.weapon.types.throwable.ThrowableWeapon;

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

	public ThrowableWeapon parse(NodeReader root, NodeReader shoot, ConfigReport report, WeaponBaseData base)
			throws InvalidConfigurationException {
		if (shoot == null) {
			throw new InvalidConfigurationException("Throw/Shoot section not found for throwable weapon");
		}

		int     fuseTime        = shoot.get("Fuse_Time").asInt().min(0).orDefault(60);
		double  explosionRadius = shoot.get("Explosion_Radius").asDouble().min(0).orDefault(3.0);
		int     explosionDamage = shoot.get("Explosion_Damage").asInt().min(0).orDefault(6);
		int     fireTicks       = shoot.get("Fire_Ticks").asInt().min(0).orDefault(0);
		boolean bounces         = shoot.get("Bounces").asBool().orDefault(false);
		int     maxBounces      = shoot.get("Max_Bounces").asInt().min(0).orDefault(5);
		boolean sticky          = shoot.get("Sticky").asBool().orDefault(false);
		String  entityType      = shoot.get("Entity_Type").asString().orDefault("SNOWBALL");

		if (bounces && sticky) {
			throw new InvalidConfigurationException("Throwable cannot have both Bounces and Sticky enabled");
		}

		ThrowableType type          = ThrowableType.getType(shoot.get("Type").asString().required().orNull());
		List<String>  effects       = shoot.get("Effects").asList().ofStrings().orEmpty();
		int           cloudDuration = shoot.get("Cloud_Duration").asInt().min(0).orDefault(0);
		double        cloudRadius   = shoot.get("Cloud_Radius").asDouble().min(0).orDefault(0.0);

		MappingNode displaySection = shoot.get("Display_Item").asMapping().orNull();
		ItemStack displayItem = parseDisplayItem(displaySection == null ? null : NodeReader.of(displaySection, report),
		                                         base.fileName());

		if (type == ThrowableType.SMOKE && cloudDuration <= 0) {
			throw new InvalidConfigurationException(
					"Throwable '" + base.fileName() + "' has Type: SMOKE but Cloud_Duration is missing or <= 0");
		}
		if (type == ThrowableType.STUN && effects.isEmpty()) {
			throw new InvalidConfigurationException(
					"Throwable '" + base.fileName() + "' has Type: STUN but Effects list is empty");
		}

		ThrowableData throwableData = new ThrowableData(fuseTime, explosionRadius, explosionDamage, fireTicks,
		                                                bounces, maxBounces, sticky, entityType,
		                                                type, effects, cloudDuration, cloudRadius, displayItem);
		ParsedAmmo     parsed         = ammoParser.parse(root, report);
		ReloadData     reloadData     = parsed != null ? parsed.reload() : null;
		AmmunitionData ammunitionData = parsed != null ? parsed.ammo() : null;

		return new ThrowableWeapon(null, base.fileName(), base.displayName(), base.category(),
		                           base.material(), base.customModelData(), base.durability(), base.lore(),
		                           base.dropHologram(), base.deathMessages(), throwableData, reloadData,
		                           ammunitionData);
	}

	/**
	 * Parses an optional {@code Display_Item:} subsection. Returns {@code null} when absent so the caller falls back to
	 * the weapon's held material.
	 */
	private ItemStack parseDisplayItem(NodeReader display, String fileName) {
		if (display == null) return null;

		String materialString = display.get("Material").asString().required().orNull();
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

		String name = display.get("Name").asString().orNull();
		if (name != null) builder.setDisplayName(name);

		List<String> lore = display.get("Lore").asList().ofStrings().orEmpty();
		if (!lore.isEmpty()) builder.setLore(lore);

		int customModelData = display.get("Custom_Model_Data").asInt().min(0).orDefault(0);
		if (customModelData > 0) builder.setCustomModelData(customModelData);

		return builder.build();
	}

}
