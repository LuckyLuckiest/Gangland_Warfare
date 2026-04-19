package me.luckyraven.weapon.configuration.parser;

import me.luckyraven.persistence.config.ConfigReport;
import me.luckyraven.persistence.config.MappingNode;
import me.luckyraven.persistence.config.NodeReader;
import me.luckyraven.weapon.SelectiveFire;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.configuration.parser.AmmunitionSectionParser.ParsedAmmo;
import me.luckyraven.weapon.dto.AmmunitionData;
import me.luckyraven.weapon.dto.ProjectileData;
import me.luckyraven.weapon.dto.ReloadData;
import me.luckyraven.weapon.projectile.ProjectileType;
import me.luckyraven.weapon.types.gun.GunWeapon;
import org.bukkit.configuration.InvalidConfigurationException;

/**
 * Parses the {@code Shoot:} section of a GUN weapon YAML and constructs a {@link GunWeapon}. Ammunition is required for
 * guns — throws if the {@code Ammunition} section is absent or invalid.
 */
public class GunWeaponParser {

	private final AmmunitionSectionParser ammoParser;

	public GunWeaponParser(AmmunitionManager ammunitionManager) {
		this.ammoParser = new AmmunitionSectionParser(ammunitionManager);
	}

	public GunWeapon parse(NodeReader root, NodeReader shoot, ConfigReport report, WeaponBaseData base)
			throws InvalidConfigurationException {
		if (shoot == null) throw new InvalidConfigurationException("Shoot section not found");

		SelectiveFireSectionParser.ParsedSelectiveFire parsedSelectiveFire =
				SelectiveFireSectionParser.parse(shoot, report, base.fileName());
		if (parsedSelectiveFire == null) {
			throw new InvalidConfigurationException(
					"Gun weapon '" + base.fileName() + "' is missing Selective_Fire under Shoot:");
		}
		SelectiveFire selectiveFire = parsedSelectiveFire.current();

		MappingNode projectileSection = shoot.get("Projectile").asMapping().required().orNull();
		if (projectileSection == null) {
			throw new InvalidConfigurationException(
					"Gun weapon '" + base.fileName() + "' is missing Projectile under Shoot:");
		}

		NodeReader projectile = NodeReader.of(projectileSection, report);

		int            projectileSpeed      = projectile.get("Speed").asInt().min(0).required().orDefault(0);
		String         projectileTypeString = projectile.get("Type").asString().required().orDefault("BULLET");
		ProjectileType projectileType       = ProjectileType.getType(projectileTypeString);

		MappingNode damageSection = projectile.get("Damage").asMapping().required().orNull();
		if (damageSection == null) {
			throw new InvalidConfigurationException(
					"Gun weapon '" + base.fileName() + "' is missing Damage under Shoot.Projectile:");
		}

		NodeReader damage = NodeReader.of(damageSection, report);

		int projectileDamage          = damage.get("Base").asInt().min(0).required().orDefault(0);
		int projectileExplosionDamage = damage.get("Explosion_Damage").asInt().min(0).orDefault(0);
		int projectileFireTicks       = damage.get("Fire_Ticks").asInt().min(0).orDefault(0);
		int projectileHeadDamage      = damage.get("Head").asInt().min(0).orDefault(0);

		int criticalHitChance = 0;
		int criticalHitDamage = 0;

		MappingNode criticalHitSection = damage.get("Critical_Hit").asMapping().orNull();
		if (criticalHitSection != null) {
			NodeReader crit = NodeReader.of(criticalHitSection, report);
			criticalHitChance = crit.get("Chance").asInt().min(0).max(100).orDefault(0);
			criticalHitDamage = crit.get("Amount").asInt().min(0).orDefault(0);
		}

		int projectileConsumed = projectile.get("Consumed_Amount").asInt().min(0).orDefault(0);
		int projectilePerShot  = projectile.get("Per_Shot").asInt().min(1).orDefault(1);
		// Cooldown is authored as a decimal (e.g. 0.8) and Bukkit's getInt silently truncated to 0. Preserve that
		// semantic by reading as double and casting — keeps existing configs working without surfacing a type error.
		int     projectileCooldown = (int) projectile.get("Cooldown").asDouble().min(0).orDefault(0.0);
		int     projectileDistance = projectile.get("Distance").asInt().min(0).orDefault(0);
		boolean projectileParticle = projectile.get("Particle").asBool().orDefault(false);
		double  projectileGravity  = projectile.get("Gravity").asDouble().orDefault(0.0);

		MappingNode weaponConsumedSection = shoot.get("Weapon_Consumed").asMapping().required().orNull();
		int         weaponConsumedOnShot  = 0;
		if (weaponConsumedSection != null) {
			weaponConsumedOnShot = NodeReader.of(weaponConsumedSection, report)
			                                 .get("Consume_On_Shot").asInt().min(0).orDefault(0);
		}

		ParsedAmmo parsed = ammoParser.parse(root, report);
		if (parsed == null) throw new InvalidConfigurationException("Ammunition section not found or invalid");

		ReloadData     reloadData     = parsed.reload();
		AmmunitionData ammunitionData = parsed.ammo();

		ProjectileData projectileData = ProjectileData.builder()
		                                              .speed(projectileSpeed)
		                                              .type(projectileType)
		                                              .damage(projectileDamage)
		                                              .consumed(projectileConsumed)
		                                              .perShot(projectilePerShot)
		                                              .cooldown(projectileCooldown)
		                                              .distance(projectileDistance)
		                                              .particle(projectileParticle)
		                                              .gravity(projectileGravity)
		                                              .build();

		GunWeapon gun = new GunWeapon(null, base.fileName(), base.displayName(), base.category(),
		                              base.material(), base.customModelData(), base.durability(), base.lore(),
		                              base.dropHologram(), base.deathMessages(), selectiveFire, weaponConsumedOnShot,
		                              projectileData, reloadData, ammunitionData);

		gun.setAllowedSelectiveFires(parsedSelectiveFire.allowed());

		gun.getDamageData().setExplosionDamage(projectileExplosionDamage);
		gun.getDamageData().setFireTicks(projectileFireTicks);
		gun.getDamageData().setHeadDamage(projectileHeadDamage);
		gun.getDamageData().setCriticalHitChance(criticalHitChance);
		gun.getDamageData().setCriticalHitDamage(criticalHitDamage);

		return gun;
	}

}
