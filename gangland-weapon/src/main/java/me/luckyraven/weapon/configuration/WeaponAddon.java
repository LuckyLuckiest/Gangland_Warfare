package me.luckyraven.weapon.configuration;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.weapon.SelectiveFire;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.dto.*;
import me.luckyraven.weapon.modifiers.*;
import me.luckyraven.weapon.projectile.ProjectileType;
import me.luckyraven.weapon.reload.ReloadType;
import me.luckyraven.weapon.types.WeaponType;
import me.luckyraven.weapon.util.BlockGroupResolver;
import org.bukkit.Color;
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

		// dispatch non-GUN types to their own parsers before gun-only sections
		if (category != WeaponType.GUN) {
			Weapon weapon = switch (category) {
				case THROWABLE -> parseThrowable(config, fileName, displayName, category, material, durability, lore,
												 dropHologram);
				case MELEE -> parseMelee(config, fileName, displayName, category, material, durability, lore,
										 dropHologram);
				case INCENDIARY -> parseIncendiary(config, fileName, displayName, category, material, durability, lore,
												   dropHologram, ammunitionAddon);
				case BIOLOGICAL -> parseBiological(config, fileName, displayName, category, material, durability, lore,
												   dropHologram, ammunitionAddon);
				default -> parseMinimal(fileName, displayName, category, material, durability, lore, dropHologram);
			};
			weapon.getDurabilityData().setOnShot(onShotDurability);
			weapon.getDurabilityData().setOnRepair(onRepairDurability);
			weapons.put(fileName, weapon);
			return;
		}

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
					.stream().map(string -> string.split(";")).toList();
		}

		// sound
		ConfigurationSection soundSection = shootSection.getConfigurationSection("Sound");
		// shot
		SoundConfiguration defaultShotSound = null;
		SoundConfiguration customShotSound  = null;
		// empty mag
		SoundConfiguration defaultMagSound = null;
		SoundConfiguration customMagSound  = null;
		// flyby
		SoundConfiguration flybyDefaultSound = null;
		SoundConfiguration flybyCustomSound  = null;
		double             flybyRange        = 0D;
		// impact
		SoundConfiguration impactDefaultSound = null;
		SoundConfiguration impactCustomSound  = null;

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

			flybyRange = soundSection.getDouble("Flyby_Range", 0D);

			ConfigurationSection flybyDefaultSection = soundSection.getConfigurationSection("Flyby_Default_Sound");
			if (flybyDefaultSection != null) {
				String sound  = Objects.requireNonNull(flybyDefaultSection.getString("Sound"));
				float  volume = (float) flybyDefaultSection.getDouble("Volume");
				float  pitch  = (float) flybyDefaultSection.getDouble("Pitch");

				flybyDefaultSound = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, sound, volume, pitch);
			}

			ConfigurationSection flybyCustomSection = soundSection.getConfigurationSection("Flyby_Custom_Sound");
			if (flybyCustomSection != null) {
				String sound  = Objects.requireNonNull(flybyCustomSection.getString("Sound"));
				float  volume = (float) flybyCustomSection.getDouble("Volume");
				float  pitch  = (float) flybyCustomSection.getDouble("Pitch");

				flybyCustomSound = new SoundConfiguration(SoundConfiguration.SoundType.CUSTOM, sound, volume, pitch);
			}

			ConfigurationSection impactDefaultSection = soundSection.getConfigurationSection("Impact_Default_Sound");
			if (impactDefaultSection != null) {
				String sound  = Objects.requireNonNull(impactDefaultSection.getString("Sound"));
				float  volume = (float) impactDefaultSection.getDouble("Volume");
				float  pitch  = (float) impactDefaultSection.getDouble("Pitch");

				impactDefaultSound = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, sound, volume, pitch);
			}

			ConfigurationSection impactCustomSection = soundSection.getConfigurationSection("Impact_Custom_Sound");
			if (impactCustomSection != null) {
				String sound  = Objects.requireNonNull(impactCustomSection.getString("Sound"));
				float  volume = (float) impactCustomSection.getDouble("Volume");
				float  pitch  = (float) impactCustomSection.getDouble("Pitch");

				impactCustomSound = new SoundConfiguration(SoundConfiguration.SoundType.CUSTOM, sound, volume, pitch);
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

		/* modifiers section */
		ConfigurationSection     modifiersSection      = config.getConfigurationSection("Modifiers");
		List<BlockBreakModifier> breakBlockModifiers   = new ArrayList<>();
		PenetrationModifier      penetrationModifier   = null;
		List<RicochetModifier>   ricochetModifiers     = new ArrayList<>();
		TracerModifier           tracerModifier        = null;
		ArmorPiercingModifier    armorPiercingModifier = null;
		FlatDamageModifier       flatDamageModifier    = null;

		if (modifiersSection != null) {
			// Break Blocks parsing
			List<String> breakBlocksList = modifiersSection.getStringList("Break_Blocks");
			for (String entry : breakBlocksList) {
				// Format: MATERIAL-hits (e.g., GLASS-3)
				String[] parts = entry.split("-");
				if (parts.length != 2) continue;

				String materialName = parts[0].trim();
				int    hitsRequired;
				try {
					hitsRequired = Integer.parseInt(parts[1].trim());
				} catch (NumberFormatException e) {
					continue;
				}

				Set<Material> materials = BlockGroupResolver.resolve(materialName);
				if (!materials.isEmpty()) {
					breakBlockModifiers.add(new BlockBreakModifier(materials, hitsRequired));
				}
			}

			// Penetration parsing - Format: blocks-entities-damageReduction (e.g., 2-3-0.25)
			String penetrationString = modifiersSection.getString("Penetration");
			if (penetrationString != null) {
				String[] parts = penetrationString.split("-");
				if (parts.length == 3) {
					try {
						int    penetrateBlocks   = Integer.parseInt(parts[0].trim());
						int    penetrateEntities = Integer.parseInt(parts[1].trim());
						double damageReduction   = Double.parseDouble(parts[2].trim());
						penetrationModifier = new PenetrationModifier(penetrateBlocks, penetrateEntities,
																	  damageReduction);
					} catch (NumberFormatException ignored) { }
				}
			}

			// Ricochet parsing - Format list: maxBounces-MATERIAL1,MATERIAL2-damageRetention
			List<String> ricochetList = modifiersSection.getStringList("Ricochet");
			for (String entry : ricochetList) {
				String[] parts = entry.split("-");
				if (parts.length == 3) {
					try {
						int maxBounces = Integer.parseInt(parts[0].trim());

						Set<Material> bounceOffBlocks = new HashSet<>();
						String[]      materialNames   = parts[1].trim().split(",");
						for (String matName : materialNames) {
							Set<Material> resolved = BlockGroupResolver.resolve(matName.trim());
							bounceOffBlocks.addAll(resolved);
						}

						double damageRetention = Double.parseDouble(parts[2].trim());
						ricochetModifiers.add(new RicochetModifier(maxBounces, bounceOffBlocks, damageRetention));
					} catch (NumberFormatException ignored) { }
				}
			}

			// Tracer parsing - Format: RRGGBB-glowing-particleSize (e.g., FF0000-true-1.5)
			String tracerString = modifiersSection.getString("Tracer");
			if (tracerString != null) {
				String[] parts = tracerString.split("-");
				if (parts.length == 3) {
					try {
						// Parse hex color
						String colorHex = parts[0].trim();
						int    red      = Integer.parseInt(colorHex.substring(0, 2), 16);
						int    green    = Integer.parseInt(colorHex.substring(2, 4), 16);
						int    blue     = Integer.parseInt(colorHex.substring(4, 6), 16);
						Color  color    = Color.fromRGB(red, green, blue);

						boolean glowing      = Boolean.parseBoolean(parts[1].trim());
						float   particleSize = Float.parseFloat(parts[2].trim());
						tracerModifier = new TracerModifier(color, glowing, particleSize);
					} catch (NumberFormatException | IndexOutOfBoundsException ignored) { }
				}
			}

			// Armor Piercing parsing - Format: bypassPercentage (e.g., 0.5)
			String armorPiercingString = modifiersSection.getString("Armor_Piercing");
			if (armorPiercingString != null) {
				try {
					double armorBypass = Double.parseDouble(armorPiercingString.trim());
					armorPiercingModifier = new ArmorPiercingModifier(armorBypass);
				} catch (NumberFormatException ignored) { }
			}

			// Flat Damage parsing - Format: bonus (e.g., 10.0)
			String flatDamageString = modifiersSection.getString("Flat_Damage");
			if (flatDamageString != null) {
				try {
					double bonus = Double.parseDouble(flatDamageString.trim());
					if (bonus > 0) {
						flatDamageModifier = new FlatDamageModifier(bonus);
					}
				} catch (NumberFormatException ignored) { }
			}
		}

		// Build the immutable data objects first
		ProjectileData projectileData = ProjectileData.builder()
													  .speed(projectileSpeed)
													  .type(projectileType)
													  .damage(projectileDamage)
													  .consumed(projectileConsumed)
													  .perShot(projectilePerShot)
													  .cooldown(projectileCooldown)
													  .distance(projectileDistance)
													  .particle(projectileParticle)
													  .build();

		ReloadData reloadData = ReloadData.builder()
										  .maxMagCapacity(reloadCapacity)
										  .cooldown(reloadCooldown)
										  .ammoType(ammoType)
										  .consume(reloadConsume)
										  .restore(reloadRestore)
										  .type(reloadType)
										  .build();

		// Create the weapon with the new constructor
		Weapon weapon = new Weapon(null, fileName, displayName, category, material, durability, lore, dropHologram,
								   selectiveFire, weaponConsumedOnShot, projectileData, reloadData, null, null, null,
								   null);

		// Set mutable data via the data objects
		weapon.getDurabilityData().setOnShot(onShotDurability);
		weapon.getDurabilityData().setOnRepair(onRepairDurability);
		weapon.getDurabilityData().setConsumeOnTime(weaponConsumedTime);

		weapon.getDamageData().setExplosionDamage(projectileExplosionDamage);
		weapon.getDamageData().setFireTicks(projectileFireTicks);
		weapon.getDamageData().setHeadDamage(projectileHeadDamage);
		weapon.getDamageData().setCriticalHitChance(projectileCriticalHitChance);
		weapon.getDamageData().setCriticalHitDamage(projectileCriticalHitDamage);

		weapon.getSpreadData().setStart(spreadStart);
		weapon.getSpreadData().setResetTime(spreadResetTime);
		weapon.getSpreadData().setChangeBase(spreadChangeBase);
		weapon.getSpreadData().setResetOnBound(spreadResetOnBound);
		weapon.getSpreadData().setBoundMinimum(spreadBoundMinimum);
		weapon.getSpreadData().setBoundMaximum(spreadBoundMaximum);

		weapon.getRecoilData().setAmount(recoilAmount);
		weapon.getRecoilData().setPushVelocity(pushVelocity);
		weapon.getRecoilData().setPushPowerUp(pushPowerUp);
		weapon.getRecoilData().setPattern(recoilPattern);

		weapon.getSoundData().setShotDefault(defaultShotSound);
		weapon.getSoundData().setShotCustom(customShotSound);
		weapon.getSoundData().setEmptyMagDefault(defaultMagSound);
		weapon.getSoundData().setEmptyMagCustom(customMagSound);
		weapon.getSoundData().setReloadDefaultBefore(reloadDefaultSoundBefore);
		weapon.getSoundData().setReloadDefaultAfter(reloadDefaultSoundAfter);
		weapon.getSoundData().setReloadCustomStart(reloadCustomSoundStart);
		weapon.getSoundData().setReloadCustomMid(reloadCustomSoundMid);
		weapon.getSoundData().setReloadCustomEnd(reloadCustomSoundEnd);
		weapon.getSoundData().setScopeDefault(scopeDefaultSound);
		weapon.getSoundData().setScopeCustom(scopeCustomSound);
		weapon.getSoundData().setFlybyDefault(flybyDefaultSound);
		weapon.getSoundData().setFlybyCustom(flybyCustomSound);
		weapon.getSoundData().setFlybyRange(flybyRange);
		weapon.getSoundData().setImpactDefault(impactDefaultSound);
		weapon.getSoundData().setImpactCustom(impactCustomSound);

		weapon.getReloadActionBarData().setReloading(reloadActionBarReloading);
		weapon.getReloadActionBarData().setOpening(reloadActionBarOpening);

		weapon.getScopeData().setLevel(scopeLevel);

		for (BlockBreakModifier modifier : breakBlockModifiers) {
			weapon.getModifiersData().addBreakBlock(modifier);
		}

		weapon.getModifiersData().setPenetration(penetrationModifier);

		for (RicochetModifier modifier : ricochetModifiers) {
			weapon.getModifiersData().addRicochet(modifier);
		}

		weapon.getModifiersData().setTracer(tracerModifier);
		weapon.getModifiersData().setArmorPiercing(armorPiercingModifier);
		weapon.getModifiersData().setFlatDamage(flatDamageModifier);

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

	private Weapon parseThrowable(FileConfiguration config, String fileName, String displayName, WeaponType category,
								  Material material, short durability, List<String> lore, boolean dropHologram) throws
			InvalidConfigurationException {
		ConfigurationSection section = config.getConfigurationSection("Throwable");
		if (section == null) throw new InvalidConfigurationException("Throwable section not found");

		int     fuseTime        = section.getInt("Fuse_Time", 60);
		double  explosionRadius = section.getDouble("Explosion_Radius", 3.0);
		int     explosionDamage = section.getInt("Explosion_Damage", 6);
		int     fireTicks       = section.getInt("Fire_Ticks", 0);
		boolean bounces         = section.getBoolean("Bounces", false);
		String  entityType      = section.getString("Entity_Type", "SNOWBALL");

		ThrowableData throwableData = new ThrowableData(fuseTime, explosionRadius, explosionDamage, fireTicks, bounces,
														entityType);
		return new Weapon(null, fileName, displayName, category, material, durability, lore, dropHologram,
						  SelectiveFire.SINGLE, 0, null, null, throwableData, null, null, null);
	}

	private Weapon parseMelee(FileConfiguration config, String fileName, String displayName, WeaponType category,
							  Material material, short durability, List<String> lore, boolean dropHologram) throws
			InvalidConfigurationException {
		ConfigurationSection section = config.getConfigurationSection("Melee");
		if (section == null) throw new InvalidConfigurationException("Melee section not found");

		double damage    = section.getDouble("Damage", 4.0);
		double range     = section.getDouble("Range", 2.5);
		int    cooldown  = section.getInt("Cooldown", 10);
		double knockback = section.getDouble("Knockback", 0.5);

		MeleeData meleeData = new MeleeData(damage, range, cooldown, knockback);
		return new Weapon(null, fileName, displayName, category, material, durability, lore, dropHologram,
						  SelectiveFire.SINGLE, 0, null, null, null, meleeData, null, null);
	}

	private Weapon parseIncendiary(FileConfiguration config, String fileName, String displayName, WeaponType category,
								   Material material, short durability, List<String> lore, boolean dropHologram,
								   AmmunitionAddon ammunitionAddon) throws InvalidConfigurationException {
		ConfigurationSection section = config.getConfigurationSection("Incendiary");
		if (section == null) throw new InvalidConfigurationException("Incendiary section not found");

		double coneAngle       = section.getDouble("Cone_Angle", 30.0);
		double range           = section.getDouble("Range", 5.0);
		int    fireDuration    = section.getInt("Fire_Duration", 60);
		int    tickRate        = section.getInt("Tick_Rate", 2);
		String ammoTypeString  = section.getString("Ammo_Type");
		int    fuelCapacity    = section.getInt("Fuel_Capacity", 100);
		int    fuelConsumeRate = section.getInt("Fuel_Consume_Rate", 2);

		IncendiaryData incendiaryData = new IncendiaryData(coneAngle, range, fireDuration, tickRate, ammoTypeString,
														   fuelCapacity, fuelConsumeRate);
		return new Weapon(null, fileName, displayName, category, material, durability, lore, dropHologram,
						  SelectiveFire.SINGLE, 0, null, null, null, null, incendiaryData, null);
	}

	private Weapon parseBiological(FileConfiguration config, String fileName, String displayName, WeaponType category,
								   Material material, short durability, List<String> lore, boolean dropHologram,
								   AmmunitionAddon ammunitionAddon) throws InvalidConfigurationException {
		ConfigurationSection section = config.getConfigurationSection("Biological");
		if (section == null) throw new InvalidConfigurationException("Biological section not found");

		int          chargeTimePerLevel = section.getInt("Charge_Time_Per_Level", 20);
		int          maxChargeLevel     = section.getInt("Max_Charge_Level", 3);
		List<String> effectsPerLevel    = section.getStringList("Effects_Per_Level");
		double       areaRadius         = section.getDouble("Area_Radius", 5.0);
		String       ammoTypeString     = section.getString("Ammo_Type");

		BiologicalData biologicalData = new BiologicalData(chargeTimePerLevel, maxChargeLevel, effectsPerLevel,
														   areaRadius, ammoTypeString);
		return new Weapon(null, fileName, displayName, category, material, durability, lore, dropHologram,
						  SelectiveFire.SINGLE, 0, null, null, null, null, null, biologicalData);
	}

	private Weapon parseMinimal(String fileName, String displayName, WeaponType category, Material material,
								short durability, List<String> lore, boolean dropHologram) {
		return new Weapon(null, fileName, displayName, category, material, durability, lore, dropHologram,
						  SelectiveFire.SINGLE, 0, null, null, null, null, null, null);
	}

}
