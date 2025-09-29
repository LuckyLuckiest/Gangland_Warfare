package me.luckyraven.feature.weapon;

import com.cryptomorin.xseries.XPotion;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.exception.PluginException;
import me.luckyraven.feature.weapon.ammo.Ammunition;
import me.luckyraven.feature.weapon.projectile.ProjectileType;
import me.luckyraven.feature.weapon.reload.Reload;
import me.luckyraven.feature.weapon.reload.ReloadType;
import me.luckyraven.file.configuration.SoundConfiguration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

@Getter
@Setter
public class Weapon implements Cloneable {

	// Information configuration
	private final UUID   uuid;
	private final String name, displayName;
	private final WeaponType   category;
	private final Material     material;
	private final short        durability;
	private final List<String> lore;
	private final boolean      dropHologram;
	private final int          weaponConsumedOnShot;

	// Shoot-projectile configuration
	private final double         projectileSpeed;
	private final ProjectileType projectileType;
	private final double         projectileDamage;
	private final int            projectileConsumed;
	private final int            projectilePerShot;
	private final int            projectileCooldown;
	private final int            projectileDistance;
	private final boolean        particle;


	private final int                    maxMagCapacity;
	private final int                    reloadCooldown;
	private final Ammunition             reloadAmmoType;
	private final int                    reloadConsume;
	private final int                    reloadRestore;
	private final ReloadType             reloadType;
	private final Map<WeaponTag, Object> tags;

	// Reload configuration
	@Getter(value = AccessLevel.NONE)
	@Setter(value = AccessLevel.NONE)
	private Reload reload;

	private String changingDisplayName;

	// Shoot configuration
	private SelectiveFire currentSelectiveFire;
	private int           currentMagCapacity;

	// Information-durability configuration
	private int durabilityOnShot;
	private int durabilityOnRepair;

	// Shoot-weapon consume configuration
	private int weaponConsumeOnTime;

	// Shoot-damage configuration
	private double projectileExplosionDamage;
	private int    projectileFireTicks;
	private double projectileHeadDamage;
	private double projectileBodyDamage;
	private int    projectileCriticalHitChance;
	private double projectileCriticalHitDamage;

	// Shot-spread configuration
	private double  spreadStart;
	private int     spreadResetTime;
	private double  spreadChangeBase;
	private boolean spreadResetOnBound;
	private double  spreadBoundMinimum;
	private double  spreadBoundMaximum;

	// Shot-recoil configuration
	private double         recoilAmount;
	private double         pushVelocity;
	private double         pushPowerUp;
	private List<String[]> recoilPattern;

	// Shot-sound configuration
	// shot
	private SoundConfiguration shotDefaultSound;
	private SoundConfiguration shotCustomSound;

	// empty mag
	private SoundConfiguration EmptyMagDefaultSound;
	private SoundConfiguration EmptyMagCustomSound;

	// Reload-sound configuration
	private SoundConfiguration reloadDefaultSoundBefore;
	private SoundConfiguration reloadDefaultSoundAfter;
	private SoundConfiguration reloadCustomSoundStart;
	private SoundConfiguration reloadCustomSoundMid;
	private SoundConfiguration reloadCustomSoundEnd;

	// Reload-action bar configuration
	private String reloadActionBarReloading;
	private String reloadActionBarOpening;

	// Scope configuration
	private int     scopeLevel;
	private boolean scoped;

	// Scope-sound configuration
	private SoundConfiguration scopeDefaultSound;
	private SoundConfiguration scopeCustomSound;

	public Weapon(UUID uuid, String name, String displayName, WeaponType category, Material material, short durability,
				  List<String> lore, boolean dropHologram, SelectiveFire selectiveFire, int weaponConsumedOnShot,
				  double projectileSpeed, ProjectileType projectileType, double projectileDamage,
				  int projectileConsumed, int projectilePerShot, int projectileCooldown, int projectileDistance,
				  boolean particle, int maxMagCapacity, int reloadCooldown, Ammunition reloadAmmoType,
				  int reloadConsume, int reloadRestore, ReloadType reloadType) {
		this.uuid                 = uuid;
		this.name                 = name;
		this.displayName          = displayName;
		this.category             = category;
		this.material             = material;
		this.durability           = durability;
		this.lore                 = lore;
		this.dropHologram         = dropHologram;
		this.currentSelectiveFire = selectiveFire;
		this.weaponConsumedOnShot = weaponConsumedOnShot;
		this.projectileSpeed      = projectileSpeed;
		this.projectileType       = projectileType;
		this.projectileDamage     = projectileDamage;
		this.projectileConsumed   = projectileConsumed;
		this.projectilePerShot    = projectilePerShot;
		this.projectileCooldown   = projectileCooldown;
		this.projectileDistance   = projectileDistance;
		this.particle             = particle;
		this.maxMagCapacity       = maxMagCapacity;
		this.currentMagCapacity   = maxMagCapacity;
		this.reloadCooldown       = reloadCooldown;
		this.reloadAmmoType       = reloadAmmoType;
		this.reloadConsume        = reloadConsume;
		this.reloadRestore        = reloadRestore == -1 ? maxMagCapacity : reloadRestore;
		this.reloadType           = reloadType;
		this.tags                 = new TreeMap<>();
		this.changingDisplayName  = updateDisplayName(displayName);
		this.reload               = reloadType.createInstance(this, reloadAmmoType);
	}

	public Weapon(String name, String displayName, WeaponType category, Material material, short durability,
				  List<String> lore, boolean dropHologram, SelectiveFire selectiveFire, int weaponConsumedOnShot,
				  double projectileSpeed, ProjectileType projectileType, double projectileDamage,
				  int projectileConsumed, int projectilePerShot, int projectileCooldown, int projectileDistance,
				  boolean particle, int maxMagCapacity, int reloadCooldown, Ammunition reloadAmmoType,
				  int reloadConsume, int reloadRestore, ReloadType reloadType) {
		this(null, name, displayName, category, material, durability, lore, dropHologram, selectiveFire,
			 weaponConsumedOnShot, projectileSpeed, projectileType, projectileDamage, projectileConsumed,
			 projectilePerShot, projectileCooldown, projectileDistance, particle, maxMagCapacity, reloadCooldown,
			 reloadAmmoType, reloadConsume, reloadRestore, reloadType);
	}

	public Weapon(UUID uuid, Weapon weapon) {
		this(uuid, weapon.name, weapon.displayName, weapon.category, weapon.material, weapon.durability, weapon.lore,
			 weapon.dropHologram, weapon.currentSelectiveFire, weapon.weaponConsumedOnShot, weapon.projectileSpeed,
			 weapon.projectileType, weapon.projectileDamage, weapon.projectileConsumed, weapon.projectilePerShot,
			 weapon.projectileCooldown, weapon.projectileDistance, weapon.particle, weapon.maxMagCapacity,
			 weapon.reloadCooldown, weapon.reloadAmmoType, weapon.reloadConsume, weapon.reloadRestore,
			 weapon.reloadType);

		this.durabilityOnShot            = weapon.durabilityOnShot;
		this.durabilityOnRepair          = weapon.durabilityOnRepair;
		this.weaponConsumeOnTime         = weapon.weaponConsumeOnTime;
		this.projectileExplosionDamage   = weapon.projectileExplosionDamage;
		this.projectileFireTicks         = weapon.projectileFireTicks;
		this.projectileHeadDamage        = weapon.projectileHeadDamage;
		this.projectileBodyDamage        = weapon.projectileBodyDamage;
		this.projectileCriticalHitChance = weapon.projectileCriticalHitChance;
		this.projectileCriticalHitDamage = weapon.projectileCriticalHitDamage;
		this.spreadStart                 = weapon.spreadStart;
		this.spreadResetTime             = weapon.spreadResetTime;
		this.spreadChangeBase            = weapon.spreadChangeBase;
		this.spreadResetOnBound          = weapon.spreadResetOnBound;
		this.spreadBoundMinimum          = weapon.spreadBoundMinimum;
		this.spreadBoundMaximum          = weapon.spreadBoundMaximum;
		this.recoilAmount                = weapon.recoilAmount;
		this.pushVelocity                = weapon.pushVelocity;
		this.pushPowerUp                 = weapon.pushPowerUp;
		this.recoilPattern               = new ArrayList<>(weapon.recoilPattern);
		this.shotDefaultSound            = weapon.shotDefaultSound.clone();
		this.shotCustomSound             = weapon.shotCustomSound.clone();
		this.EmptyMagDefaultSound        = weapon.EmptyMagDefaultSound.clone();
		this.EmptyMagCustomSound         = weapon.EmptyMagCustomSound.clone();
		this.reloadDefaultSoundBefore    = weapon.reloadDefaultSoundBefore.clone();
		this.reloadDefaultSoundAfter     = weapon.reloadDefaultSoundAfter.clone();
		this.reloadCustomSoundStart      = weapon.reloadCustomSoundStart.clone();
		this.reloadCustomSoundMid        = weapon.reloadCustomSoundMid.clone();
		this.reloadCustomSoundEnd        = weapon.reloadCustomSoundEnd.clone();
		this.reloadActionBarReloading    = weapon.reloadActionBarReloading;
		this.reloadActionBarOpening      = weapon.reloadActionBarOpening;
		this.scopeLevel                  = weapon.scopeLevel;
		this.scopeDefaultSound           = weapon.scopeDefaultSound.clone();
		this.scopeCustomSound            = weapon.scopeCustomSound.clone();
	}

	public static String getTagProperName(WeaponTag tag) {
		return tag.name().toLowerCase().replaceAll("_", "-");
	}

	private static WeaponTag[] getAllTags() {
		return WeaponTag.values();
	}

	public boolean isReloading() {
		return reload.isReloading();
	}

	public void reload(JavaPlugin plugin, Player player, boolean removeAmmunition) {
		reload.reload(plugin, player, removeAmmunition);
	}

	public void stopReloading() {
		reload.stopReloading();
	}

	public void scope(Player player, boolean bypass) {
		if (!bypass) if (scoped) return;

		this.scoped = true;

		applyEffect(player, XPotion.SLOWNESS, scopeLevel);
		applyEffect(player, XPotion.JUMP_BOOST, 250);
	}

	public void unScope(Player player, boolean bypass) {
		if (!bypass) if (!scoped) return;

		this.scoped = false;

		removeEffect(player, XPotion.SLOWNESS);
		removeEffect(player, XPotion.JUMP_BOOST);
	}

	public void updateWeaponData(ItemBuilder itemBuilder) {
		// display name
		this.changingDisplayName = updateDisplayName(displayName);
		itemBuilder.setDisplayName(changingDisplayName);

		boolean updatedSelectiveFire = false, updatedCurrentAmmo = false;

		// add non-available tags
		for (WeaponTag tag : getAllTags()) {
			if (containsTag(itemBuilder, tag)) continue;

			switch (tag) {
				case UUID -> tags.put(tag, uuid.toString());
				case WEAPON -> tags.put(tag, name);
				case SELECTIVE_FIRE -> {
					tags.put(tag, currentSelectiveFire);
					updatedSelectiveFire = true;
				}
				case AMMO_LEFT -> {
					tags.put(tag, currentMagCapacity);
					updatedCurrentAmmo = true;
				}
			}

			itemBuilder.addTag(getTagProperName(tag), tags.get(tag));
		}

		// selective fire
		if (!updatedSelectiveFire) updateTag(itemBuilder, WeaponTag.SELECTIVE_FIRE, currentSelectiveFire);

		// mag capacity
		if (!updatedCurrentAmmo) updateTag(itemBuilder, WeaponTag.AMMO_LEFT, currentMagCapacity);
	}

	public void updateWeapon(Player player, ItemBuilder itemBuilder, int slot) {
		player.getInventory().setItem(slot, itemBuilder.build());
	}

	public void removeWeapon(Player player, int slot) {
		player.getInventory().setItem(slot, new ItemStack(Material.AIR));
	}

	public boolean containsTag(ItemBuilder itemBuilder, WeaponTag tag) {
		return itemBuilder.hasNBTTag(getTagProperName(tag));
	}

	public boolean isMagazineFull() {
		return currentMagCapacity >= maxMagCapacity;
	}

	public boolean isMagazineEmpty() {
		return currentMagCapacity <= 0;
	}

	public void addAmmunition(int amount) {
		currentMagCapacity = Math.min(maxMagCapacity, currentMagCapacity + amount);
	}

	/**
	 * Consumes a shot depending on the amount set.
	 *
	 * @return Whether a shot was consumed or not.
	 */
	public boolean consumeShot() {
		if (isMagazineEmpty()) return false;

		currentMagCapacity = Math.max(0, currentMagCapacity - projectileConsumed);
		return true;
	}

	public ItemStack buildItem() {
		ItemBuilder builder = new ItemBuilder(material);

		builder.setDisplayName(changingDisplayName).setLore(lore).setDurability(durability);
		initializeTags(builder);
		builder.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

		return builder.build();
	}

	@Override
	public Weapon clone() {
		try {
			Weapon weapon = (Weapon) super.clone();

			// current mag capacity
			weapon.currentMagCapacity = weapon.maxMagCapacity;
			// remove all tags set
			weapon.tags.clear();
			// copy the recoil pattern
			weapon.recoilPattern = new ArrayList<>(this.recoilPattern);

			// sounds
			weapon.shotDefaultSound         = this.shotDefaultSound.clone();
			weapon.shotCustomSound          = this.shotCustomSound.clone();
			weapon.EmptyMagDefaultSound     = this.EmptyMagDefaultSound.clone();
			weapon.EmptyMagCustomSound      = this.EmptyMagCustomSound.clone();
			weapon.reloadDefaultSoundBefore = this.reloadDefaultSoundBefore.clone();
			weapon.reloadDefaultSoundAfter  = this.reloadDefaultSoundAfter.clone();
			weapon.reloadCustomSoundStart   = this.reloadCustomSoundStart.clone();
			weapon.reloadCustomSoundMid     = this.reloadCustomSoundMid.clone();
			weapon.reloadCustomSoundEnd     = this.reloadCustomSoundEnd.clone();
			weapon.scopeDefaultSound        = this.scopeDefaultSound.clone();
			weapon.scopeCustomSound         = this.scopeCustomSound.clone();

			weapon.reload = this.reload.clone();

			return weapon;
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}
	}

	@Override
	public String toString() {
		return String.format(
				"Weapon{uuid='%s',name='%s',displayName='%s',category='%s',material='%s',durability=%d,lore='%s'," +
				"dropHologram=%b,weaponConsumedOnShot=%b,projectileSpeed=%.2f,projectileType='%s',projectileDamage=%.2f," +
				"projectileConsumed=%d,projectilePerShot=%d,projectileCooldown=%d,projectileDistance=%d," +
				"particle='%s',maxMagCapacity=%d,reloadCooldown=%d,reloadAmmoType='%s',reloadConsume=%d," +
				"reloadRestore=%d,reloadType='%s',tags='%s',reload='%s',changingDisplayName='%s',currentSelectiveFire=%b," +
				"currentMagCapacity=%d,durabilityOnShot=%d,durabilityOnRepair=%d,weaponConsumeOnTime=%b," +
				"projectileExplosionDamage=%.2f,projectileFireTicks=%d,projectileHeadDamage=%.2f," +
				"projectileBodyDamage=%.2f,projectileCriticalHitChance=%d,projectileCriticalHitDamage=%.2f," +
				"spreadStart=%.2f,spreadResetTime=%d,spreadChangeBase=%.2f,spreadResetOnBound=%b," +
				"spreadBoundMinimum=%.2f,spreadBoundMaximum=%.2f,recoilAmount=%.2f,pushVelocity=%.2f,pushPowerUp=%b," +
				"recoilPattern='%s',reloadActionBarReloading='%s',reloadActionBarOpening='%s',scopeLevel=%d,scoped=%b}",
				uuid.toString(), name, displayName, category, material, durability, lore, dropHologram,
				weaponConsumedOnShot, projectileSpeed, projectileType, projectileDamage, projectileConsumed,
				projectilePerShot, projectileCooldown, projectileDistance, particle, maxMagCapacity, reloadCooldown,
				reloadAmmoType, reloadConsume, reloadRestore, reloadType, tags, reload, changingDisplayName,
				currentSelectiveFire, currentMagCapacity, durabilityOnShot, durabilityOnRepair, weaponConsumeOnTime,
				projectileExplosionDamage, projectileFireTicks, projectileHeadDamage, projectileBodyDamage,
				projectileCriticalHitChance, projectileCriticalHitDamage, spreadStart, spreadResetTime,
				spreadChangeBase, spreadResetOnBound, spreadBoundMinimum, spreadBoundMaximum, recoilAmount,
				pushVelocity, pushPowerUp, recoilPattern.stream().map(Arrays::toString).toList(),
				reloadActionBarReloading, reloadActionBarOpening, scopeLevel, scoped);
	}

	private void updateTag(ItemBuilder itemBuilder, WeaponTag tag, Object value) {
		tags.replace(tag, value);
		itemBuilder.modifyTag(getTagProperName(tag), value);
	}

	private String updateDisplayName(String displayName) {
		return displayName + " &8«&6" + currentMagCapacity + "&7/&6" + maxMagCapacity + "&8»";
	}

	private void initializeTags(ItemBuilder itemBuilder) {
		tags.put(WeaponTag.UUID, uuid.toString());
		tags.put(WeaponTag.WEAPON, name);
		tags.put(WeaponTag.SELECTIVE_FIRE, currentSelectiveFire);
		tags.put(WeaponTag.AMMO_LEFT, currentMagCapacity);

		for (WeaponTag tag : tags.keySet())
			itemBuilder.addTag(getTagProperName(tag), tags.get(tag));
	}

	private void applyEffect(Player player, XPotion potion, int amplifier) {
		Optional<XPotion> optional = XPotion.of(potion.name());
		if (optional.isEmpty()) return;

		PotionEffectType potionEffectType = optional.get().getPotionEffectType();

		if (potionEffectType == null) return;

		player.addPotionEffect(new PotionEffect(potionEffectType, Integer.MAX_VALUE, amplifier));
	}

	private void removeEffect(Player player, XPotion potion) {
		Optional<XPotion> optional = XPotion.of(potion.name());
		if (optional.isEmpty()) return;

		PotionEffectType potionEffectType = optional.get().getPotionEffectType();

		if (potionEffectType == null) return;

		player.removePotionEffect(potionEffectType);
	}

}
