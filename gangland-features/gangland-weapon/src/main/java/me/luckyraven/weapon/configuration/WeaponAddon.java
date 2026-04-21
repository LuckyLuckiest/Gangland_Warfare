package me.luckyraven.weapon.configuration;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import me.luckyraven.core.Placeholder;
import me.luckyraven.core.configuration.SoundConfiguration;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.config.ConfigReport;
import me.luckyraven.persistence.config.FileHandlerReader;
import me.luckyraven.persistence.config.MappingNode;
import me.luckyraven.persistence.config.NodeReader;
import me.luckyraven.weapon.SelectiveFire;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.configuration.parser.*;
import me.luckyraven.weapon.dto.*;
import me.luckyraven.weapon.types.WeaponType;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@CustomLog
public class WeaponAddon {

	private final Map<String, Weapon> weapons;

	/**
	 * Placeholder resolver handed over by the impl side at init time. Each parsed {@link Weapon} has this injected
	 * after construction so its {@code buildItem} / {@code updateWeaponData} calls resolve {@code %gangland_*%} tokens
	 * in the display name and lore.
	 */
	@Nullable
	private final Placeholder placeholder;

	public WeaponAddon(@Nullable Placeholder placeholder) {
		this.weapons     = new HashMap<>();
		this.placeholder = placeholder;
	}

	public void registerWeapon(AmmunitionManager ammunitionManager, FileHandler fileHandler) throws
			InvalidConfigurationException {
		String       fileName = fileHandler.getName().toLowerCase();
		ConfigReport report   = new ConfigReport();
		NodeReader   root     = FileHandlerReader.read(fileHandler, report);

		String configVersion = root.get("Config_Version").asString().orNull();
		if (configVersion != null) {
			return;
		}

		/* information section */
		MappingNode informationSection = root.get("Information").asMapping().required().orNull();
		if (informationSection == null) {
			throw new InvalidConfigurationException("Information section not found for '" + fileName + "'");
		}

		NodeReader information = NodeReader.of(informationSection, report);

		String displayName = information.get("Name").asString().required().orNull();

		String     categoryString = information.get("Category").asString().required().orNull();
		WeaponType category       = WeaponType.getType(Objects.requireNonNull(categoryString));

		String              materialString    = information.get("Material").asString().required().orNull();
		Optional<XMaterial> xMaterialOptional = XMaterial.matchXMaterial(Objects.requireNonNull(materialString));
		Material            material;
		if (xMaterialOptional.isPresent()) material = xMaterialOptional.get().get();
		else material = XMaterial.FEATHER.get();

		int customModelData = information.get("Custom_Model_Data").asInt().min(0).orDefault(0);

		MappingNode durabilitySection = information.get("Durability").asMapping().required().orNull();
		short       durability        = 0;
		short       onShotDurability  = 0;
		if (durabilitySection != null) {
			NodeReader dur = NodeReader.of(durabilitySection, report);
			durability = (short) dur.get("Base").asInt().min(0).required().orDefault(0);

			MappingNode changeSection = dur.get("Change").asMapping().orNull();
			if (changeSection != null) {
				onShotDurability = (short) NodeReader.of(changeSection, report)
				                                     .get("On_Shot").asInt().orDefault(0);
			}
		}

		List<String> lore         = information.get("Lore").asList().ofStrings().orEmpty();
		boolean      dropHologram = information.get("Drop_Hologram").asBool().orDefault(false);

		List<String> deathMessages = root.get("Death_Messages").asList().ofStrings().orEmpty();
		if (deathMessages.isEmpty()) deathMessages = null;

		WeaponBaseData base = new WeaponBaseData(fileName, displayName, category, material, customModelData, durability,
		                                         lore, dropHologram, deathMessages);

		/* dispatch to type-specific parser */
		MappingNode shootSection = resolveShootSection(root);
		NodeReader  shoot        = shootSection != null ? NodeReader.of(shootSection, report) : null;

		Weapon weapon = switch (category) {
			case GUN, OTHER -> new GunWeaponParser(ammunitionManager).parse(root, shoot, report, base);
			case THROWABLE -> new ThrowableWeaponParser(ammunitionManager).parse(root, shoot, report, base);
			case MELEE -> new MeleeWeaponParser(ammunitionManager).parse(root, shoot, report, base);
			case INCENDIARY -> new IncendiaryWeaponParser(ammunitionManager).parse(root, shoot, report, base);
			case BIOLOGICAL -> new BiologicalWeaponParser(ammunitionManager).parse(root, shoot, report, base);
		};

		/* apply shared post-parse sections */
		weapon.setDurabilityData(new DurabilityData());
		weapon.setSoundData(new SoundData());
		weapon.getDurabilityData().setOnShot(onShotDurability);
		applyShootSounds(shoot, weapon, report);
		applyReloadSoundsAndActionBar(root, weapon, report);
		applyOptionalShootConfig(shoot, weapon, report);
		applyScope(root, weapon, report);
		ModifiersSectionParser.apply(root, weapon, report);

		// hand the placeholder resolver to the weapon instance so its rendering path can resolve
		// %gangland_*% tokens in display name and lore
		weapon.setPlaceholder(placeholder);

		if (!report.isEmpty()) report.log(log);

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
	private MappingNode resolveShootSection(NodeReader root) {
		MappingNode section = root.get("Shoot").asMapping().orNull();
		if (section != null) return section;
		section = root.get("Attack").asMapping().orNull();
		if (section != null) return section;
		section = root.get("Throw").asMapping().orNull();
		if (section != null) return section;
		section = root.get("Melee").asMapping().orNull();
		if (section != null) return section;
		return root.get("Throwable").asMapping().orNull();
	}

	// -------------------------------------------------------------------------
	// Shared post-parse helpers
	// -------------------------------------------------------------------------

	private void applyOptionalShootConfig(@Nullable NodeReader shoot, Weapon weapon, ConfigReport report) {
		if (shoot == null) return;

		String selectiveFireString = shoot.get("Selective_Fire").asString().orNull();
		if (selectiveFireString != null) {
			weapon.setCurrentSelectiveFire(SelectiveFire.getType(selectiveFireString));
		}

		MappingNode weaponConsumedSection = shoot.get("Weapon_Consumed").asMapping().orNull();
		if (weaponConsumedSection != null) {
			int consumeOnTime = NodeReader.of(weaponConsumedSection, report)
			                              .get("Time").asInt().orDefault(-1);
			if (consumeOnTime == 0) consumeOnTime = -1;
			weapon.getDurabilityData().setConsumeOnTime(consumeOnTime);
		}

		MappingNode recoilSection = shoot.get("Recoil").asMapping().orNull();
		if (recoilSection != null) {
			NodeReader recoil = NodeReader.of(recoilSection, report);
			weapon.setRecoilData(new RecoilData());
			weapon.getRecoilData().setAmount(recoil.get("Amount").asDouble().orDefault(0.0));
			weapon.getRecoilData().setPushVelocity(recoil.get("Push").asDouble().orDefault(0.0));
			weapon.getRecoilData().setPushPowerUp(recoil.get("Power_Up").asDouble().orDefault(0.0));
			weapon.getRecoilData().setPattern(
					recoil.get("Pattern").asList().ofStrings().orEmpty()
							.stream().map(s -> s.split(";")).toList());
		}

		MappingNode spreadSection = shoot.get("Spread").asMapping().orNull();
		if (spreadSection == null) return;

		NodeReader spread = NodeReader.of(spreadSection, report);

		weapon.setSpreadData(new SpreadData());
		weapon.getSpreadData().setStart(spread.get("Starting_Spread").asDouble().orDefault(0.0));
		weapon.getSpreadData().setResetTime(spread.get("Time").asInt().orDefault(0));

		MappingNode spreadChangeSection = spread.get("Change").asMapping().orNull();
		if (spreadChangeSection == null) return;

		NodeReader change = NodeReader.of(spreadChangeSection, report);
		weapon.getSpreadData().setChangeBase(change.get("Base").asDouble().orDefault(0.0));

		MappingNode boundSection = change.get("Bounds").asMapping().orNull();
		if (boundSection == null) return;

		NodeReader bounds = NodeReader.of(boundSection, report);
		weapon.getSpreadData().setResetOnBound(bounds.get("Reset_On_Bound").asBool().orDefault(false));
		weapon.getSpreadData().setBoundMinimum(bounds.get("Min").asDouble().orDefault(0.0));
		weapon.getSpreadData().setBoundMaximum(bounds.get("Max").asDouble().orDefault(0.0));
	}

	private void applyScope(NodeReader root, Weapon weapon, ConfigReport report) {
		MappingNode scopeSection = root.get("Scope").asMapping().orNull();
		if (scopeSection == null) return;

		NodeReader scope = NodeReader.of(scopeSection, report);

		weapon.setScopeData(new ScopeData());
		weapon.getScopeData().setLevel(scope.get("Level").asInt().orDefault(0));

		MappingNode soundSection = scope.get("Sound").asMapping().orNull();
		if (soundSection == null) return;

		NodeReader sound = NodeReader.of(soundSection, report);

		weapon.getSoundData().setScopeDefault(parseSound(sound, "Default_Sound",
		                                                 SoundConfiguration.SoundType.VANILLA, report));
		weapon.getSoundData().setScopeCustom(parseSound(sound, "Custom_Sound",
		                                                SoundConfiguration.SoundType.CUSTOM, report));
	}

	private void applyShootSounds(@Nullable NodeReader shoot, Weapon weapon, ConfigReport report) {
		if (shoot == null) return;
		MappingNode soundSection = shoot.get("Sound").asMapping().orNull();
		if (soundSection == null) return;

		NodeReader sound = NodeReader.of(soundSection, report);

		weapon.getSoundData().setShotDefault(parseSound(sound, "Default_Sound",
		                                                SoundConfiguration.SoundType.VANILLA, report));
		weapon.getSoundData().setShotCustom(parseSound(sound, "Custom_Sound",
		                                               SoundConfiguration.SoundType.CUSTOM, report));
		weapon.getSoundData().setEmptyMagDefault(parseSound(sound, "Empty_Default_Sound",
		                                                    SoundConfiguration.SoundType.VANILLA, report));
		weapon.getSoundData().setEmptyMagCustom(parseSound(sound, "Empty_Custom_Sound",
		                                                   SoundConfiguration.SoundType.CUSTOM, report));

		double flybyRange = sound.get("Flyby_Range").asDouble().orDefault(0.0);
		weapon.getSoundData().setFlybyRange(flybyRange);
		weapon.getSoundData().setFlybyDefault(parseSound(sound, "Flyby_Default_Sound",
		                                                 SoundConfiguration.SoundType.VANILLA, report));
		weapon.getSoundData().setFlybyCustom(parseSound(sound, "Flyby_Custom_Sound",
		                                                SoundConfiguration.SoundType.CUSTOM, report));
		weapon.getSoundData().setImpactDefault(parseSound(sound, "Impact_Default_Sound",
		                                                  SoundConfiguration.SoundType.VANILLA, report));
		weapon.getSoundData().setImpactCustom(parseSound(sound, "Impact_Custom_Sound",
		                                                 SoundConfiguration.SoundType.CUSTOM, report));
	}

	private void applyReloadSoundsAndActionBar(NodeReader root, Weapon weapon, ConfigReport report) {
		MappingNode reloadSection = root.get("Reload").asMapping().orNull();
		if (reloadSection == null) return;

		NodeReader reload = NodeReader.of(reloadSection, report);

		MappingNode reloadSoundSection = reload.get("Sound").asMapping().orNull();
		if (reloadSoundSection != null) {
			NodeReader reloadSound = NodeReader.of(reloadSoundSection, report);

			weapon.getSoundData().setReloadDefaultBefore(parseSound(reloadSound, "Default_Sound_Before",
			                                                        SoundConfiguration.SoundType.VANILLA, report));
			weapon.getSoundData().setReloadDefaultAfter(parseSound(reloadSound, "Default_Sound_After",
			                                                       SoundConfiguration.SoundType.VANILLA, report));

			MappingNode customSoundSection = reloadSound.get("Custom_Sound").asMapping().orNull();
			if (customSoundSection != null) {
				NodeReader custom = NodeReader.of(customSoundSection, report);
				weapon.getSoundData().setReloadCustomStart(parseSound(custom, "Start",
				                                                      SoundConfiguration.SoundType.CUSTOM, report));
				weapon.getSoundData().setReloadCustomMid(parseSound(custom, "Mid",
				                                                    SoundConfiguration.SoundType.CUSTOM, report));
				weapon.getSoundData().setReloadCustomEnd(parseSound(custom, "End",
				                                                    SoundConfiguration.SoundType.CUSTOM, report));
			}
		}

		MappingNode actionBarSection = reload.get("Action_Bar").asMapping().orNull();
		if (actionBarSection != null) {
			NodeReader actionBar = NodeReader.of(actionBarSection, report);
			weapon.setReloadActionBarData(new ReloadActionBarData());
			weapon.getReloadActionBarData().setReloading(actionBar.get("Reloading").asString().orNull());
			weapon.getReloadActionBarData().setOpening(actionBar.get("Opening").asString().orNull());
		}
	}

	@Nullable
	private SoundConfiguration parseSound(NodeReader parent, String key, SoundConfiguration.SoundType type,
	                                      ConfigReport report) {
		MappingNode section = parent.get(key).asMapping().orNull();
		if (section == null) return null;
		NodeReader r     = NodeReader.of(section, report);
		String     sound = r.get("Sound").asString().orNull();
		if (sound == null) return null;
		float volume = (float) r.get("Volume").asDouble().orDefault(1.0);
		float pitch  = (float) r.get("Pitch").asDouble().orDefault(1.0);
		return new SoundConfiguration(type, sound, volume, pitch);
	}

}
