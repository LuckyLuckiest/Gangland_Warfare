package me.luckyraven.weapon.types.gun;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.weapon.SelectiveFire;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.dto.AmmunitionData;
import me.luckyraven.weapon.dto.DamageData;
import me.luckyraven.weapon.dto.ProjectileData;
import me.luckyraven.weapon.dto.ReloadData;
import me.luckyraven.weapon.types.WeaponType;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class GunWeapon extends Weapon {

	// Gun-specific immutable configuration
	private final ProjectileData projectileData;
	private final int            weaponConsumedOnShot;

	// Gun-specific mutable configuration
	private DamageData damageData;

	public GunWeapon(UUID uuid, String name, String displayName, WeaponType category, Material material,
	                 short durability, List<String> lore, boolean dropHologram, @Nullable List<String> deathMessages,
	                 SelectiveFire selectiveFire, int weaponConsumedOnShot, ProjectileData projectileData,
	                 ReloadData reloadData, @Nullable AmmunitionData ammunitionData) {
		super(uuid, name, displayName, category, material, durability, lore, dropHologram, deathMessages, reloadData,
		      ammunitionData);
		this.projectileData       = projectileData;
		this.weaponConsumedOnShot = weaponConsumedOnShot;
		this.setCurrentSelectiveFire(selectiveFire);

		this.damageData = new DamageData();
	}

	// --- Magazine override (uses projectile consumed amount per shot) ---

	@Override
	public boolean consumeShot() {
		if (isMagazineEmpty()) return false;
		setCurrentMagCapacity(Math.max(0, getCurrentMagCapacity() - projectileData.getConsumed()));
		return true;
	}

	// --- Overrides ---

	@Override
	public GunWeapon copyWithUUID(UUID newUuid) {
		GunWeapon copy = this.clone();
		copy.setUUID(newUuid);
		return copy;
	}

	@Override
	public GunWeapon clone() {
		// super.clone() (Weapon.clone()) performs Object.clone() + initClone(),
		// covering tags, durabilityData, soundData, reloadActionBarData,
		// modifiersData, recoilData, scopeData, spreadData,
		// recoil/spread managers, durabilityCalculator, currentMagCapacity, and reload.
		GunWeapon copy = (GunWeapon) super.clone();

		// Clone Gun-specific mutable data
		copy.damageData = this.damageData.clone();

		return copy;
	}

	@Override
	public int compareTo(@NotNull Weapon other) {
		int base = super.compareTo(other);
		if (base != 0 || !(other instanceof GunWeapon otherGunWeapon)) return base;
		return Comparator.<GunWeapon, Double>comparing(
								 g -> g.projectileData != null ? g.projectileData.getSpeed() : 0.0)
		                 .thenComparing(g -> g.projectileData != null ? g.projectileData.getType().name() : "")
		                 .thenComparingDouble(g -> g.projectileData != null ? g.projectileData.getDamage() : 0.0)
		                 .thenComparingInt(g -> g.projectileData != null ? g.projectileData.getConsumed() : 0)
		                 .thenComparingInt(g -> g.projectileData != null ? g.projectileData.getPerShot() : 0)
		                 .thenComparingInt(g -> g.projectileData != null ? g.projectileData.getCooldown() : 0)
		                 .thenComparingInt(g -> g.projectileData != null ? g.projectileData.getDistance() : 0)
		                 .thenComparing(g -> g.projectileData != null && g.projectileData.isParticle())
		                 .thenComparingInt(
								 g -> g.getAmmunitionData() != null ? g.getAmmunitionData().getMaxMagCapacity() : 0)
		                 .thenComparingInt(g -> g.getReloadData() != null ? g.getReloadData().getCooldown() : 0)
		                 .thenComparing(g -> g.getAmmunitionData() != null ?
		                                     g.getAmmunitionData().getAmmoType().getName() :
		                                     "")
		                 .thenComparingInt(
								 g -> g.getAmmunitionData() != null ? g.getAmmunitionData().getConsumeRate() : 0)
		                 .thenComparingInt(g -> g.getAmmunitionData() != null ? g.getAmmunitionData().getRestore() : 0)
		                 .thenComparing(g -> g.getReloadData() != null ? g.getReloadData().getType().name() : "")
		                 .compare(this, otherGunWeapon);
	}

}
