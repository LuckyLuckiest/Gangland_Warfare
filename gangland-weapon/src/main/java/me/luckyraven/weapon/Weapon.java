package me.luckyraven.weapon;

import com.cryptomorin.xseries.XPotion;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.luckyraven.exception.PluginException;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.weapon.dto.*;
import me.luckyraven.weapon.durability.DurabilityCalculator;
import me.luckyraven.weapon.projectile.recoil.RecoilManager;
import me.luckyraven.weapon.projectile.spread.SpreadManager;
import me.luckyraven.weapon.reload.Reload;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Getter
@Setter
public class Weapon implements Cloneable, Comparable<Weapon> {

	// Core identity (immutable)
	private final UUID         uuid;
	private final String       name;
	private final String       displayName;
	private final WeaponType   category;
	private final Material     material;
	private final short        durability;
	private final List<String> lore;
	private final boolean      dropHologram;
	private final int          weaponConsumedOnShot;

	// Configuration groups (immutable)
	private final ProjectileData         projectileData;
	private final ReloadData             reloadData;
	// Runtime state
	private final Map<WeaponTag, Object> tags;
	// Configuration groups (mutable)
	private       DurabilityData         durabilityData;
	private       DamageData             damageData;
	private       SpreadData             spreadData;
	private       RecoilData             recoilData;
	private       SoundData              soundData;
	private       ScopeData              scopeData;
	private       ReloadActionBarData    reloadActionBarData;
	private       String                 changingDisplayName;
	private       short                  currentDurability;
	private       SelectiveFire          currentSelectiveFire;
	private       int                    currentMagCapacity;

	// Managers (non-serialized)
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private Reload               reload;
	@Setter(AccessLevel.NONE)
	private DurabilityCalculator durabilityCalculator;
	@Setter(AccessLevel.NONE)
	private SpreadManager        spread;
	@Setter(AccessLevel.NONE)
	private RecoilManager        recoil;

	public Weapon(UUID uuid, String name, String displayName, WeaponType category, Material material, short durability,
				  List<String> lore, boolean dropHologram, SelectiveFire selectiveFire, int weaponConsumedOnShot,
				  ProjectileData projectileData, ReloadData reloadData) {
		this.uuid                 = uuid;
		this.name                 = name;
		this.displayName          = displayName;
		this.category             = category;
		this.material             = material;
		this.durability           = durability;
		this.currentDurability    = durability;
		this.lore                 = lore;
		this.dropHologram         = dropHologram;
		this.currentSelectiveFire = selectiveFire;
		this.weaponConsumedOnShot = weaponConsumedOnShot;
		this.projectileData       = projectileData;
		this.reloadData           = reloadData;

		this.currentMagCapacity  = reloadData.getMaxMagCapacity();
		this.tags                = new TreeMap<>();
		this.changingDisplayName = updateDisplayName(displayName);

		initializeMutableData();
		initializeManagers();
	}

	public Weapon(UUID uuid, Weapon weapon) {
		this(uuid, weapon.name, weapon.displayName, weapon.category, weapon.material, weapon.durability, weapon.lore,
			 weapon.dropHologram, weapon.currentSelectiveFire, weapon.weaponConsumedOnShot, weapon.projectileData,
			 weapon.reloadData);

		this.currentDurability = weapon.currentDurability;
		copyMutableData(weapon);
	}

	public static String getTagProperName(WeaponTag tag) {
		return tag.name().toLowerCase().replace("_", "-");
	}

	// Reload operations
	public boolean isReloading() { return reload.isReloading(); }

	public void reload(JavaPlugin plugin, Player player, boolean removeAmmunition) {
		reload.reload(plugin, player, removeAmmunition);
	}

	public void stopReloading() { reload.stopReloading(); }

	// Scope operations
	public void scope(Player player, boolean bypass) {
		if (!bypass && scopeData.isScoped()) return;
		scopeData.setScoped(true);
		applyEffect(player, XPotion.SLOWNESS, scopeData.getLevel());
		applyEffect(player, XPotion.JUMP_BOOST, 250);
	}

	public void unScope(Player player, boolean bypass) {
		if (!bypass && !scopeData.isScoped()) return;
		scopeData.setScoped(false);
		removeEffect(player, XPotion.SLOWNESS);
		removeEffect(player, XPotion.JUMP_BOOST);
	}

	// Durability operations
	public void increaseDurability(ItemBuilder itemBuilder, int amount) {
		durabilityCalculator.setDurability(itemBuilder, (short) (currentDurability + amount));
	}

	public void decreaseDurability(ItemBuilder itemBuilder, int amount) {
		durabilityCalculator.setDurability(itemBuilder, (short) (currentDurability - amount));
	}

	public boolean isBroken() { return currentDurability <= 0; }

	// Magazine operations
	public boolean isMagazineFull() { return currentMagCapacity >= reloadData.getMaxMagCapacity(); }

	public boolean isMagazineEmpty() { return currentMagCapacity <= 0; }

	public void addAmmunition(int amount) {
		currentMagCapacity = Math.min(reloadData.getMaxMagCapacity(), currentMagCapacity + amount);
	}

	public boolean consumeShot() {
		if (isMagazineEmpty()) return false;
		currentMagCapacity = Math.max(0, currentMagCapacity - projectileData.getConsumed());
		return true;
	}

	public boolean requiresReload(boolean normalCheck) {
		if (!normalCheck) return !isMagazineFull();
		boolean hasSpaceForAmmo = reloadData.getMaxMagCapacity() - currentMagCapacity < reloadData.getRestore();
		return !isMagazineFull() || hasSpaceForAmmo;
	}

	// Weapon item operations
	public void updateWeaponData(ItemBuilder itemBuilder) {
		this.changingDisplayName = updateDisplayName(displayName);
		itemBuilder.setDisplayName(changingDisplayName);

		boolean updatedSelectiveFire = false, updatedCurrentAmmo = false;

		for (WeaponTag tag : WeaponTag.values()) {
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

		if (!updatedSelectiveFire) updateTag(itemBuilder, WeaponTag.SELECTIVE_FIRE, currentSelectiveFire);
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

	public ItemStack buildItem() {
		ItemBuilder builder = new ItemBuilder(material);
		builder.setDisplayName(changingDisplayName).setLore(lore);

		short currentDamage = (short) Math.floor(
				(durability - currentDurability) * (builder.getItemMaxDurability() / (double) durability));
		builder.setDurability(currentDamage);

		initializeTags(builder);
		builder.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
		return builder.build();
	}

	@Override
	public Weapon clone() {
		try {
			Weapon weapon = (Weapon) super.clone();

			weapon.currentMagCapacity = weapon.reloadData.getMaxMagCapacity();
			weapon.tags.clear();
			weapon.copyMutableData(this);

			weapon.reload               = this.reload.clone();
			weapon.recoil               = new RecoilManager(weapon);
			weapon.spread               = new SpreadManager(weapon);
			weapon.durabilityCalculator = new DurabilityCalculator(weapon);
			return weapon;
		} catch (CloneNotSupportedException e) {
			throw new PluginException(e);
		}
	}

	@Override
	public int compareTo(@NotNull Weapon other) {
		return Comparator.comparing(Weapon::getName, String.CASE_INSENSITIVE_ORDER)
						 .thenComparing(Weapon::getCategory)
						 .thenComparing(Weapon::getMaterial)
						 .thenComparingInt(w -> w.durability)
						 .thenComparingDouble(w -> w.projectileData.getSpeed())
						 .thenComparing(w -> w.projectileData.getType())
						 .thenComparingDouble(w -> w.projectileData.getDamage())
						 .thenComparingInt(w -> w.projectileData.getConsumed())
						 .thenComparingInt(w -> w.projectileData.getPerShot())
						 .thenComparingInt(w -> w.projectileData.getCooldown())
						 .thenComparingInt(w -> w.projectileData.getDistance())
						 .thenComparing(w -> w.projectileData.isParticle())
						 .thenComparingInt(w -> w.reloadData.getMaxMagCapacity())
						 .thenComparingInt(w -> w.reloadData.getCooldown())
						 .thenComparing(w -> w.reloadData.getAmmoType())
						 .thenComparingInt(w -> w.reloadData.getConsume())
						 .thenComparingInt(w -> w.reloadData.getRestore())
						 .thenComparing(w -> w.reloadData.getType())
						 .compare(this, other);
	}

	@Override
	public String toString() {
		return String.format("Weapon{uuid='%s',name='%s',category='%s',material='%s'}", uuid, name, category, material);
	}

	private void initializeManagers() {
		this.reload               = reloadData.getType().createInstance(this, reloadData.getAmmoType());
		this.recoil               = new RecoilManager(this);
		this.spread               = new SpreadManager(this);
		this.durabilityCalculator = new DurabilityCalculator(this);
	}

	private void initializeMutableData() {
		this.durabilityData      = new DurabilityData();
		this.damageData          = new DamageData();
		this.spreadData          = new SpreadData();
		this.recoilData          = new RecoilData();
		this.soundData           = new SoundData();
		this.scopeData           = new ScopeData();
		this.reloadActionBarData = new ReloadActionBarData();
	}

	private void copyMutableData(Weapon source) {
		this.durabilityData      = source.durabilityData.clone();
		this.damageData          = source.damageData.clone();
		this.spreadData          = source.spreadData.clone();
		this.recoilData          = source.recoilData.clone();
		this.soundData           = source.soundData.clone();
		this.scopeData           = source.scopeData.clone();
		this.reloadActionBarData = source.reloadActionBarData.clone();
	}

	private void updateTag(ItemBuilder itemBuilder, WeaponTag tag, Object value) {
		tags.replace(tag, value);
		itemBuilder.modifyTag(getTagProperName(tag), value);
	}

	private String updateDisplayName(String displayName) {
		return String.format("%s&r &8«&6%d&7/&6%d&8»&r", displayName, currentMagCapacity,
							 reloadData.getMaxMagCapacity());
	}

	private void initializeTags(ItemBuilder itemBuilder) {
		tags.put(WeaponTag.UUID, uuid.toString());
		tags.put(WeaponTag.WEAPON, name);
		tags.put(WeaponTag.SELECTIVE_FIRE, currentSelectiveFire);
		tags.put(WeaponTag.AMMO_LEFT, currentMagCapacity);
		tags.forEach((tag, value) -> itemBuilder.addTag(getTagProperName(tag), value));
	}

	private void applyEffect(Player player, XPotion potion, int amplifier) {
		XPotion.of(potion.name())
			   .map(XPotion::getPotionEffectType)
			   .ifPresent(type -> player.addPotionEffect(new PotionEffect(type, Integer.MAX_VALUE, amplifier)));
	}

	private void removeEffect(Player player, XPotion potion) {
		XPotion.of(potion.name()).map(XPotion::getPotionEffectType).ifPresent(player::removePotionEffect);
	}

}