package me.luckyraven.weapon.configuration;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.weapon.SelectiveFire;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.configuration.parser.*;
import me.luckyraven.weapon.dto.*;
import me.luckyraven.weapon.modifiers.*;
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

	public void registerWeapon(AmmunitionManager ammunitionManager, FileHandler fileHandler) throws
			InvalidConfigurationException {
		FileConfiguration config   = fileHandler.getFileConfiguration();
		String            fileName = fileHandler.getName().toLowerCase();

		String configVersion = config.getString("Config_Version");
		if (configVersion != null) {
			return;
		}

		/* information section */
		ConfigurationSection informationSection = config.getConfigurationSection("Information");
		if (informationSection == null) throw new InvalidConfigurationException("Information section not found");

		String displayName = informationSection.getString("Name");

		String     categoryString = informationSection.getString("Category");
		WeaponType category       = WeaponType.getType(Objects.requireNonNull(categoryString));

		String              materialString    = informationSection.getString("Material");
		Optional<XMaterial> xMaterialOptional = XMaterial.matchXMaterial(Objects.requireNonNull(materialString));
		Material            material;
		if (xMaterialOptional.isPresent()) material = xMaterialOptional.get().get();
		else material = XMaterial.FEATHER.get();

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

		List<String> lore         = informationSection.getStringList("Lore");
		boolean      dropHologram = informationSection.getBoolean("Drop_Hologram");

		List<String> deathMessages = config.getStringList("Death_Messages");
		if (deathMessages.isEmpty()) deathMessages = null;

		WeaponBaseData base = new WeaponBaseData(fileName, displayName, category, material, durability, lore,
		                                         dropHologram, deathMessages);

		/* dispatch to type-specific parser */
		ConfigurationSection shootSection = resolveShootSection(config);

		Weapon weapon = switch (category) {
			case GUN, OTHER -> new GunWeaponParser(ammunitionManager).parse(config, base);
			case THROWABLE -> new ThrowableWeaponParser(ammunitionManager).parse(config, shootSection, base);
			case MELEE -> new MeleeWeaponParser(ammunitionManager).parse(config, shootSection, base);
			case INCENDIARY -> new IncendiaryWeaponParser(ammunitionManager).parse(config, shootSection, base);
			case BIOLOGICAL -> new BiologicalWeaponParser(ammunitionManager).parse(config, shootSection, base);
		};

		/* apply shared post-parse sections */
		weapon.setDurabilityData(new DurabilityData());
		weapon.setSoundData(new SoundData());
		weapon.getDurabilityData().setOnShot(onShotDurability);
		weapon.getDurabilityData().setOnRepair(onRepairDurability);
		applyShootSounds(shootSection, weapon);
		applyReloadSoundsAndActionBar(config, weapon);
		applyOptionalShootConfig(shootSection, weapon);
		applyScope(config, weapon);
		applyModifiers(config, weapon);

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

	// -------------------------------------------------------------------------
	// Shoot section resolution
	// -------------------------------------------------------------------------

	/**
	 * Resolves the shoot section for non-GUN weapon types. Priority: {@code Shoot:} → {@code Attack:} → {@code Throw:}
	 * → legacy {@code Melee:}/{@code Throwable:}. Returns {@code null} if none exist.
	 */
	@Nullable
	private ConfigurationSection resolveShootSection(FileConfiguration config) {
		ConfigurationSection section = config.getConfigurationSection("Shoot");
		if (section != null) return section;
		section = config.getConfigurationSection("Attack");
		if (section != null) return section;
		section = config.getConfigurationSection("Throw");
		if (section != null) return section;
		section = config.getConfigurationSection("Melee");
		if (section != null) return section;
		return config.getConfigurationSection("Throwable");
	}

	// -------------------------------------------------------------------------
	// Shared post-parse helpers
	// -------------------------------------------------------------------------

	/**
	 * Parses optional shoot-section sub-configs that apply to every weapon type.
	 */
	private void applyOptionalShootConfig(@Nullable ConfigurationSection shootSection, Weapon weapon) {
		if (shootSection == null) return;

		String selectiveFireString = shootSection.getString("Selective_Fire");
		if (selectiveFireString != null) {
			weapon.setCurrentSelectiveFire(SelectiveFire.getType(selectiveFireString));
		}

		ConfigurationSection weaponConsumedSection = shootSection.getConfigurationSection("Weapon_Consumed");
		if (weaponConsumedSection != null) {
			int consumeOnTime = weaponConsumedSection.getInt("Time", -1);
			if (consumeOnTime == 0) consumeOnTime = -1;
			weapon.getDurabilityData().setConsumeOnTime(consumeOnTime);
		}

		ConfigurationSection recoilSection = shootSection.getConfigurationSection("Recoil");
		if (recoilSection != null) {
			weapon.setRecoilData(new RecoilData());
			weapon.getRecoilData().setAmount(recoilSection.getDouble("Amount"));
			weapon.getRecoilData().setPushVelocity(recoilSection.getDouble("Push"));
			weapon.getRecoilData().setPushPowerUp(recoilSection.getDouble("Power_Up"));
			weapon.getRecoilData()
			      .setPattern(recoilSection.getStringList("Pattern")
									  .stream().map(s -> s.split(";")).toList());
		}

		ConfigurationSection spreadSection = shootSection.getConfigurationSection("Spread");
		if (spreadSection == null) return;

		weapon.setSpreadData(new SpreadData());

		weapon.getSpreadData().setStart(spreadSection.getDouble("Starting_Spread"));
		weapon.getSpreadData().setResetTime(spreadSection.getInt("Time"));

		ConfigurationSection spreadChangeSection = spreadSection.getConfigurationSection("Change");
		if (spreadChangeSection == null) return;

		weapon.getSpreadData().setChangeBase(spreadSection.getDouble("Base"));
		ConfigurationSection boundSection = spreadChangeSection.getConfigurationSection("Bounds");

		if (boundSection == null) return;
		weapon.getSpreadData().setResetOnBound(spreadSection.getBoolean("Reset_On_Bound"));
		weapon.getSpreadData().setBoundMinimum(spreadSection.getDouble("Min"));
		weapon.getSpreadData().setBoundMaximum(spreadSection.getDouble("Max"));
	}

	private void applyScope(FileConfiguration config, Weapon weapon) {
		ConfigurationSection scopeSection = config.getConfigurationSection("Scope");
		if (scopeSection == null) return;

		weapon.setScopeData(new ScopeData());
		weapon.getScopeData().setLevel(scopeSection.getInt("Level"));

		ConfigurationSection soundSection = scopeSection.getConfigurationSection("Sound");
		if (soundSection == null) return;

		weapon.getSoundData()
		      .setScopeDefault(parseSound(soundSection, "Default_Sound", SoundConfiguration.SoundType.VANILLA));
		weapon.getSoundData()
		      .setScopeCustom(parseSound(soundSection, "Custom_Sound", SoundConfiguration.SoundType.CUSTOM));
	}

	private void applyModifiers(FileConfiguration config, Weapon weapon) {
		ConfigurationSection modifiersSection = config.getConfigurationSection("Modifiers");
		if (modifiersSection == null) return;

		weapon.setModifiersData(new ModifiersData());

		for (String entry : modifiersSection.getStringList("Break_Blocks")) {
			String[] parts = entry.split("-");
			if (parts.length != 2) continue;
			try {
				Set<Material> materials = BlockGroupResolver.resolve(parts[0].trim());
				if (!materials.isEmpty()) weapon.getModifiersData()
				                                .addBreakBlock(new BlockBreakModifier(materials, Integer.parseInt(
														parts[1].trim())));
			} catch (NumberFormatException ignored) { }
		}

		String penetrationString = modifiersSection.getString("Penetration");
		if (penetrationString != null) {
			String[] parts = penetrationString.split("-");
			if (parts.length == 3) {
				try {
					weapon.getModifiersData()
					      .setPenetration(new PenetrationModifier(Integer.parseInt(parts[0].trim()),
					                                              Integer.parseInt(parts[1].trim()),
					                                              Double.parseDouble(parts[2].trim())));
				} catch (NumberFormatException ignored) { }
			}
		}

		for (String entry : modifiersSection.getStringList("Ricochet")) {
			String[] parts = entry.split("-");
			if (parts.length == 3) {
				try {
					Set<Material> bounceOffBlocks = new HashSet<>();
					for (String matName : parts[1].trim().split(","))
						bounceOffBlocks.addAll(BlockGroupResolver.resolve(matName.trim()));
					weapon.getModifiersData()
					      .addRicochet(new RicochetModifier(Integer.parseInt(parts[0].trim()), bounceOffBlocks,
					                                        Double.parseDouble(parts[2].trim())));
				} catch (NumberFormatException ignored) { }
			}
		}

		String tracerString = modifiersSection.getString("Tracer");
		if (tracerString != null) {
			String[] parts = tracerString.split("-");
			if (parts.length == 3) {
				try {
					String colorHex = parts[0].trim();
					Color color = Color.fromRGB(Integer.parseInt(colorHex.substring(0, 2), 16),
					                            Integer.parseInt(colorHex.substring(2, 4), 16),
					                            Integer.parseInt(colorHex.substring(4, 6), 16));
					weapon.getModifiersData()
					      .setTracer(new TracerModifier(color, Boolean.parseBoolean(parts[1].trim()),
					                                    Float.parseFloat(parts[2].trim())));
				} catch (NumberFormatException | IndexOutOfBoundsException ignored) { }
			}
		}

		String armorPiercingString = modifiersSection.getString("Armor_Piercing");
		if (armorPiercingString != null) {
			try {
				weapon.getModifiersData()
				      .setArmorPiercing(new ArmorPiercingModifier(Double.parseDouble(armorPiercingString.trim())));
			} catch (NumberFormatException ignored) { }
		}

		String flatDamageString = modifiersSection.getString("Flat_Damage");
		if (flatDamageString != null) {
			try {
				double bonus = Double.parseDouble(flatDamageString.trim());
				if (bonus > 0) weapon.getModifiersData().setFlatDamage(new FlatDamageModifier(bonus));
			} catch (NumberFormatException ignored) { }
		}
	}

	private void applyShootSounds(@Nullable ConfigurationSection shootSection, Weapon weapon) {
		if (shootSection == null) return;
		ConfigurationSection soundSection = shootSection.getConfigurationSection("Sound");
		if (soundSection == null) return;

		weapon.getSoundData()
		      .setShotDefault(parseSound(soundSection, "Default_Sound", SoundConfiguration.SoundType.VANILLA));
		weapon.getSoundData()
		      .setShotCustom(parseSound(soundSection, "Custom_Sound", SoundConfiguration.SoundType.CUSTOM));
		weapon.getSoundData()
		      .setEmptyMagDefault(
					  parseSound(soundSection, "Empty_Default_Sound", SoundConfiguration.SoundType.VANILLA));
		weapon.getSoundData()
		      .setEmptyMagCustom(parseSound(soundSection, "Empty_Custom_Sound", SoundConfiguration.SoundType.CUSTOM));

		double flybyRange = soundSection.getDouble("Flyby_Range", 0D);
		weapon.getSoundData().setFlybyRange(flybyRange);
		weapon.getSoundData()
		      .setFlybyDefault(parseSound(soundSection, "Flyby_Default_Sound", SoundConfiguration.SoundType.VANILLA));
		weapon.getSoundData()
		      .setFlybyCustom(parseSound(soundSection, "Flyby_Custom_Sound", SoundConfiguration.SoundType.CUSTOM));
		weapon.getSoundData()
		      .setImpactDefault(parseSound(soundSection, "Impact_Default_Sound", SoundConfiguration.SoundType.VANILLA));
		weapon.getSoundData()
		      .setImpactCustom(parseSound(soundSection, "Impact_Custom_Sound", SoundConfiguration.SoundType.CUSTOM));
	}

	private void applyReloadSoundsAndActionBar(FileConfiguration config, Weapon weapon) {
		ConfigurationSection reloadSection = config.getConfigurationSection("Reload");
		if (reloadSection == null) return;

		ConfigurationSection reloadSoundSection = reloadSection.getConfigurationSection("Sound");
		if (reloadSoundSection != null) {
			weapon.getSoundData()
			      .setReloadDefaultBefore(
						  parseSound(reloadSoundSection, "Default_Sound_Before", SoundConfiguration.SoundType.VANILLA));
			weapon.getSoundData()
			      .setReloadDefaultAfter(
						  parseSound(reloadSoundSection, "Default_Sound_After", SoundConfiguration.SoundType.VANILLA));

			ConfigurationSection customSoundSection = reloadSoundSection.getConfigurationSection("Custom_Sound");
			if (customSoundSection != null) {
				weapon.getSoundData()
				      .setReloadCustomStart(
							  parseSound(customSoundSection, "Start", SoundConfiguration.SoundType.CUSTOM));
				weapon.getSoundData()
				      .setReloadCustomMid(parseSound(customSoundSection, "Mid", SoundConfiguration.SoundType.CUSTOM));
				weapon.getSoundData()
				      .setReloadCustomEnd(parseSound(customSoundSection, "End", SoundConfiguration.SoundType.CUSTOM));
			}
		}

		ConfigurationSection actionBarSection = reloadSection.getConfigurationSection("Action_Bar");
		if (actionBarSection != null) {
			weapon.setReloadActionBarData(new ReloadActionBarData());
			weapon.getReloadActionBarData().setReloading(actionBarSection.getString("Reloading"));
			weapon.getReloadActionBarData().setOpening(actionBarSection.getString("Opening"));
		}
	}

	@Nullable
	private SoundConfiguration parseSound(ConfigurationSection parent, String key, SoundConfiguration.SoundType type) {
		ConfigurationSection section = parent.getConfigurationSection(key);
		if (section == null) return null;
		String sound = section.getString("Sound");
		if (sound == null) return null;
		float volume = (float) section.getDouble("Volume");
		float pitch  = (float) section.getDouble("Pitch");
		return new SoundConfiguration(type, sound, volume, pitch);
	}

}
