package me.luckyraven.weapon.configuration;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.file.FileHandler;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.weapon.SelectiveFire;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponType;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.projectile.ProjectileType;
import me.luckyraven.weapon.reload.ReloadType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class WeaponAddon {

	private final Map<String, Weapon> weapons;

	public WeaponAddon() {
		this.weapons = new HashMap<>();
	}

	public void registerWeapon(AmmunitionAddon ammunitionAddon, FileHandler fileHandler) throws
			InvalidConfigurationException {
		FileConfiguration config   = fileHandler.getFileConfiguration();
		String            fileName = fileHandler.getName().toLowerCase();

		String configVersion = config.getString("Config_Version");
		if (configVersion != null) {
			// recreates the file if needed
			return;
		}

		/* information section */
		ConfigurationSection informationSection = config.getConfigurationSection("Information");
		if (informationSection == null) throw new InvalidConfigurationException("Information section not found");
		// display name
		String displayName = informationSection.getString("Name");

		// category
		String     categoryString = informationSection.getString("Category");
		WeaponType category       = WeaponType.getType(Objects.requireNonNull(categoryString));

		// material
		String              materialString    = informationSection.getString("Material");
		Optional<XMaterial> xMaterialOptional = XMaterial.matchXMaterial(Objects.requireNonNull(materialString));
		Material            material;
		if (xMaterialOptional.isPresent()) material = xMaterialOptional.get().get();
		else material = XMaterial.FEATHER.get();

		// durability
		ConfigurationSection durabilitySection = Objects.requireNonNull(
				informationSection.getConfigurationSection("Durability"));
		short                durability              = (short) durabilitySection.getInt("Base");
		ConfigurationSection durabilityChangeSection = durabilitySection.getConfigurationSection("Change");
		short                onShotDurability        = 0;
		short                onRepairDurability      = 0;
		if (durabilityChangeSection != null) {
			onShotDurability   = (short) durabilityChangeSection.getInt("On_Shot");
			onRepairDurability = (short) durabilityChangeSection.getInt("On_Repair");
		}

		// lore
		List<String> lore = informationSection.getStringList("Lore");

		// drop hologram
		boolean dropHologram = informationSection.getBoolean("Drop_Hologram");

		/* shoot section */
		ConfigurationSection shootSection = config.getConfigurationSection("Shoot");
		if (shootSection == null) throw new InvalidConfigurationException("Shoot section not found");

		// selective fire
		String        selectiveFireString = shootSection.getString("Selective_Fire");
		SelectiveFire selectiveFire       = SelectiveFire.getType(Objects.requireNonNull(selectiveFireString));

		// projectile
		ConfigurationSection projectileSection = Objects.requireNonNull(
				shootSection.getConfigurationSection("Projectile"));
		// speed
		int projectileSpeed = projectileSection.getInt("Speed");
		// type
		String         projectileTypeString = projectileSection.getString("Type");
		ProjectileType projectileType       = ProjectileType.getType(Objects.requireNonNull(projectileTypeString));

		// damage
		ConfigurationSection damageSection = Objects.requireNonNull(
				projectileSection.getConfigurationSection("Damage"));
		int                  projectileDamage            = damageSection.getInt("Base");
		int                  projectileExplosionDamage   = damageSection.getInt("Explosion_Damage");
		int                  projectileFireTicks         = damageSection.getInt("Fire_Ticks");
		int                  projectileHeadDamage        = damageSection.getInt("Head");
		ConfigurationSection criticalHitSection          = damageSection.getConfigurationSection("Critical_Hit");
		int                  projectileCriticalHitChance = 0;
		int                  projectileCriticalHitDamage = 0;
		if (criticalHitSection != null) {
			projectileCriticalHitChance = criticalHitSection.getInt("Chance");
			projectileCriticalHitDamage = criticalHitSection.getInt("Amount");
		}

		// consumed proj
		int projectileConsumed = projectileSection.getInt("Consumed_Amount");

		// per shot proj
		int projectilePerShot = projectileSection.getInt("Per_Shot");

		// proj cooldown
		int projectileCooldown = projectileSection.getInt("Cooldown");

		// proj distance
		int projectileDistance = projectileSection.getInt("Distance");

		// proj particle
		boolean projectileParticle = projectileSection.getBoolean("Particle");

		// weapon consumed
		ConfigurationSection weaponConsumedSection = Objects.requireNonNull(
				shootSection.getConfigurationSection("Weapon_Consumed"));
		int weaponConsumedOnShot = weaponConsumedSection.getInt("Consume_On_Shot");
		int weaponConsumedTime   = weaponConsumedSection.getInt("Time");
		weaponConsumedTime = weaponConsumedTime == 0 ? -1 : weaponConsumedTime;

		// spread
		ConfigurationSection spreadSection      = shootSection.getConfigurationSection("Spread");
		double               spreadStart        = 0D;
		int                  spreadResetTime    = 0;
		double               spreadChangeBase   = 0D;
		boolean              spreadResetOnBound = false;
		double               spreadBoundMinimum = 0D;
		double               spreadBoundMaximum = 0D;

		if (spreadSection != null) {
			spreadStart     = spreadSection.getDouble("Starting_Spread");
			spreadResetTime = spreadSection.getInt("Time");

			ConfigurationSection spreadChangeSection = spreadSection.getConfigurationSection("Change");
			if (spreadChangeSection != null) {
				spreadChangeBase = spreadSection.getDouble("Base");

				ConfigurationSection boundSection = spreadChangeSection.getConfigurationSection("Bounds");
				if (boundSection != null) {
					spreadResetOnBound = spreadSection.getBoolean("Reset_On_Bound");
					spreadBoundMinimum = spreadSection.getDouble("Min");
					spreadBoundMaximum = spreadSection.getDouble("Max");
				}
			}
		}

		// recoil
		ConfigurationSection recoilSection = shootSection.getConfigurationSection("Recoil");
		double               recoilAmount  = 0D;
		double               pushVelocity  = 0D;
		double               pushPowerUp   = 0D;
		List<String[]>       recoilPattern = new ArrayList<>();

		if (recoilSection != null) {
			recoilAmount  = recoilSection.getDouble("Amount");
			pushVelocity  = recoilSection.getDouble("Push");
			pushPowerUp   = recoilSection.getDouble("Power_Up");
			recoilPattern = recoilSection.getStringList("Pattern")
					.stream() // stream
							  .map(string -> string.split(";")) // convert to array
							  .toList(); // back to the list
		}

		// sound
		ConfigurationSection soundSection = shootSection.getConfigurationSection("Sound");
		// shot
		SoundConfiguration defaultShotSound = null;
		SoundConfiguration customShotSound  = null;
		// empty mag
		SoundConfiguration defaultMagSound = null;
		SoundConfiguration customMagSound  = null;

		if (soundSection != null) {
			ConfigurationSection defaultSound = soundSection.getConfigurationSection("Default_Sound");
			if (defaultSound != null) {
				String sound  = Objects.requireNonNull(defaultSound.getString("Sound"));
				float  volume = (float) defaultSound.getDouble("Volume");
				float  pitch  = (float) defaultSound.getDouble("Pitch");

				defaultShotSound = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, sound, volume, pitch);
			}

			ConfigurationSection customSound = soundSection.getConfigurationSection("Custom_Sound");
			if (customSound != null) {
				String sound  = Objects.requireNonNull(customSound.getString("Sound"));
				float  volume = (float) customSound.getDouble("Volume");
				float  pitch  = (float) customSound.getDouble("Pitch");

				customShotSound = new SoundConfiguration(SoundConfiguration.SoundType.CUSTOM, sound, volume, pitch);
			}

			ConfigurationSection emptyDefaultSound = soundSection.getConfigurationSection("Empty_Default_Sound");
			if (emptyDefaultSound != null) {
				String sound  = Objects.requireNonNull(emptyDefaultSound.getString("Sound"));
				float  volume = (float) emptyDefaultSound.getDouble("Volume");
				float  pitch  = (float) emptyDefaultSound.getDouble("Pitch");

				defaultMagSound = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, sound, volume, pitch);
			}

			ConfigurationSection emptyCustomSound = soundSection.getConfigurationSection("Empty_Custom_Sound");
			if (emptyCustomSound != null) {
				String sound  = Objects.requireNonNull(emptyCustomSound.getString("Sound"));
				float  volume = (float) emptyCustomSound.getDouble("Volume");
				float  pitch  = (float) emptyCustomSound.getDouble("Pitch");

				customMagSound = new SoundConfiguration(SoundConfiguration.SoundType.CUSTOM, sound, volume, pitch);
			}
		}

		/* reload section */
		ConfigurationSection reloadSection = config.getConfigurationSection("Reload");
		if (reloadSection == null) throw new InvalidConfigurationException("Reload section not found");

		// capacity
		int reloadCapacity = reloadSection.getInt("Capacity");

		// cooldown
		int reloadCooldown = reloadSection.getInt("Cooldown");

		// ammo type
		String     ammoTypeString = reloadSection.getString("Ammo_Type");
		Ammunition ammunition     = ammunitionAddon.getAmmunition(ammoTypeString);
		Ammunition ammoType       = Objects.requireNonNull(ammunition);

		// consume
		int reloadConsume = reloadSection.getInt("Consume");

		// restore
		int reloadRestore = reloadSection.getInt("Restore");

		// sound
		ConfigurationSection reloadSoundSection       = reloadSection.getConfigurationSection("Sound");
		SoundConfiguration   reloadDefaultSoundBefore = null;
		SoundConfiguration   reloadDefaultSoundAfter  = null;
		SoundConfiguration   reloadCustomSoundStart   = null;
		SoundConfiguration   reloadCustomSoundMid     = null;
		SoundConfiguration   reloadCustomSoundEnd     = null;

		if (reloadSoundSection != null) {
			ConfigurationSection defaultSoundBefore = reloadSoundSection.getConfigurationSection(
					"Default_Sound_Before");
			if (defaultSoundBefore != null) {
				String sound  = Objects.requireNonNull(defaultSoundBefore.getString("Sound"));
				float  volume = (float) defaultSoundBefore.getDouble("Volume");
				float  pitch  = (float) defaultSoundBefore.getDouble("Pitch");

				reloadDefaultSoundBefore = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, sound, volume,
																  pitch);
			}

			ConfigurationSection defaultSoundAfter = reloadSoundSection.getConfigurationSection("Default_Sound_After");
			if (defaultSoundAfter != null) {
				String sound  = Objects.requireNonNull(defaultSoundAfter.getString("Sound"));
				float  volume = (float) defaultSoundAfter.getDouble("Volume");
				float  pitch  = (float) defaultSoundAfter.getDouble("Pitch");

				reloadDefaultSoundAfter = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, sound, volume,
																 pitch);
			}

			ConfigurationSection customSoundSection = reloadSoundSection.getConfigurationSection("Custom_Sound");
			if (customSoundSection != null) {
				ConfigurationSection customSoundStart = customSoundSection.getConfigurationSection("Start");
				if (customSoundStart != null) {
					String sound  = Objects.requireNonNull(customSoundStart.getString("Sound"));
					float  volume = (float) customSoundStart.getDouble("Volume");
					float  pitch  = (float) customSoundStart.getDouble("Pitch");

					reloadCustomSoundStart = new SoundConfiguration(SoundConfiguration.SoundType.CUSTOM, sound, volume,
																	pitch);
				}

				ConfigurationSection customSoundMid = customSoundSection.getConfigurationSection("Mid");
				if (customSoundMid != null) {
					String sound  = Objects.requireNonNull(customSoundMid.getString("Sound"));
					float  volume = (float) customSoundMid.getDouble("Volume");
					float  pitch  = (float) customSoundMid.getDouble("Pitch");

					reloadCustomSoundMid = new SoundConfiguration(SoundConfiguration.SoundType.CUSTOM, sound, volume,
																  pitch);
				}

				ConfigurationSection customSoundEnd = customSoundSection.getConfigurationSection("End");
				if (customSoundEnd != null) {
					String sound  = Objects.requireNonNull(customSoundEnd.getString("Sound"));
					float  volume = (float) customSoundEnd.getDouble("Volume");
					float  pitch  = (float) customSoundEnd.getDouble("Pitch");

					reloadCustomSoundEnd = new SoundConfiguration(SoundConfiguration.SoundType.CUSTOM, sound, volume,
																  pitch);
				}
			}
		}

		// action bar
		ConfigurationSection actionBarSection         = reloadSection.getConfigurationSection("Action_Bar");
		String               reloadActionBarReloading = null;
		String               reloadActionBarOpening   = null;

		if (actionBarSection != null) {
			reloadActionBarReloading = actionBarSection.getString("Reloading");
			reloadActionBarOpening   = actionBarSection.getString("Opening");
		}

		// reload type
		String reloadTypeString = Objects.requireNonNull(reloadSection.getString("Type"));
		String reloadTypeTemp   = reloadTypeString;
		int    reloadTypeAmount = 1;
		if (reloadTypeString.contains("-")) {
			String[] data = reloadTypeString.split("-");

			reloadTypeTemp   = data[0];
			reloadTypeAmount = Integer.parseInt(data[1]);
		}
		ReloadType reloadType = ReloadType.getType(reloadTypeTemp);
		reloadType.setAmount(reloadTypeAmount);

		/* scope section */
		ConfigurationSection scopeSection      = config.getConfigurationSection("Scope");
		int                  scopeLevel        = 0;
		SoundConfiguration   scopeDefaultSound = null;
		SoundConfiguration   scopeCustomSound  = null;

		if (scopeSection != null) {
			// scope level
			scopeLevel = scopeSection.getInt("Level");

			// sound
			ConfigurationSection scopeSoundSection = scopeSection.getConfigurationSection("Sound");
			if (scopeSoundSection != null) {
				ConfigurationSection defaultSound = scopeSoundSection.getConfigurationSection("Default_Sound");
				if (defaultSound != null) {
					String sound  = Objects.requireNonNull(defaultSound.getString("Sound"));
					float  volume = (float) defaultSound.getDouble("Volume");
					float  pitch  = (float) defaultSound.getDouble("Pitch");

					scopeDefaultSound = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, sound, volume,
															   pitch);
				}

				ConfigurationSection customSound = scopeSoundSection.getConfigurationSection("Custom_Sound");
				if (customSound != null) {
					String sound  = Objects.requireNonNull(customSound.getString("Sound"));
					float  volume = (float) customSound.getDouble("Volume");
					float  pitch  = (float) customSound.getDouble("Pitch");

					scopeCustomSound = new SoundConfiguration(SoundConfiguration.SoundType.CUSTOM, sound, volume,
															  pitch);
				}
			}
		}

		// initialize the object
		Weapon weapon = new Weapon(fileName, displayName, category, material, durability, lore, dropHologram,
								   selectiveFire, weaponConsumedOnShot, projectileSpeed, projectileType,
								   projectileDamage, projectileConsumed, projectilePerShot, projectileCooldown,
								   projectileDistance, projectileParticle, reloadCapacity, reloadCooldown, ammoType,
								   reloadConsume, reloadRestore, reloadType);

		weapon.setDurabilityOnShot(onShotDurability);
		weapon.setDurabilityOnRepair(onRepairDurability);

		weapon.setProjectileExplosionDamage(projectileExplosionDamage);
		weapon.setProjectileFireTicks(projectileFireTicks);
		weapon.setProjectileHeadDamage(projectileHeadDamage);
		weapon.setProjectileCriticalHitChance(projectileCriticalHitChance);
		weapon.setProjectileCriticalHitDamage(projectileCriticalHitDamage);

		weapon.setWeaponConsumeOnTime(weaponConsumedTime);

		weapon.setSpreadStart(spreadStart);
		weapon.setSpreadResetTime(spreadResetTime);
		weapon.setSpreadChangeBase(spreadChangeBase);
		weapon.setSpreadResetOnBound(spreadResetOnBound);
		weapon.setSpreadBoundMinimum(spreadBoundMinimum);
		weapon.setSpreadBoundMaximum(spreadBoundMaximum);

		weapon.setRecoilAmount(recoilAmount);
		weapon.setPushVelocity(pushVelocity);
		weapon.setPushPowerUp(pushPowerUp);
		weapon.setRecoilPattern(recoilPattern);

		weapon.setShotDefaultSound(defaultShotSound);
		weapon.setShotCustomSound(customShotSound);
		weapon.setEmptyMagDefaultSound(defaultMagSound);
		weapon.setEmptyMagCustomSound(customMagSound);

		weapon.setReloadDefaultSoundBefore(reloadDefaultSoundBefore);
		weapon.setReloadDefaultSoundAfter(reloadDefaultSoundAfter);
		weapon.setReloadCustomSoundStart(reloadCustomSoundStart);
		weapon.setReloadCustomSoundMid(reloadCustomSoundMid);
		weapon.setReloadCustomSoundEnd(reloadCustomSoundEnd);

		weapon.setReloadActionBarReloading(reloadActionBarReloading);
		weapon.setReloadActionBarOpening(reloadActionBarOpening);

		weapon.setScopeLevel(scopeLevel);

		weapon.setScopeDefaultSound(scopeDefaultSound);
		weapon.setScopeCustomSound(scopeCustomSound);

		weapons.put(fileName, weapon);
	}

	@Nullable
	public Weapon getWeapon(String key) {
		return weapons.get(key);
	}

	public Set<String> getWeaponKeys() {
		return weapons.keySet();
	}

	public void clear() {
		weapons.clear();
	}

	public int size() {
		return weapons.size();
	}

}
