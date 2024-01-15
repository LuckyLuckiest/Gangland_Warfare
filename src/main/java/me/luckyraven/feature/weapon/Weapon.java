package me.luckyraven.feature.weapon;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.feature.weapon.ammo.Ammunition;
import me.luckyraven.feature.weapon.projectile.ProjectileType;
import me.luckyraven.feature.weapon.reload.ReloadType;
import me.luckyraven.file.configuration.SoundConfiguration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.List;

@RequiredArgsConstructor
@Getter
@Setter
public class Weapon {

	// Information configuration
	private final String name, displayName;
	private final WeaponType   category;
	private final Material     material;
	private final short        durability;
	private final List<String> lore;
	private final boolean      dropHologram;

	// Shoot configuration
	private final SelectiveFire selectiveFire;
	private final int           weaponShotConsume;

	// Shoot-projectile configuration
	private final double         projectileSpeed;
	private final ProjectileType projectileType;
	private final double         projectileDamage;
	private final int            projectileConsumed;
	private final int            projectilePerShot;
	private final int            projectileCooldown;
	private final int            projectileDistance;
	private final boolean        particle;

	// Reload configuration
	private final int        reloadCapacity;
	private final int        reloadCooldown;
	private final Ammunition reloadAmmoType;
	private final int        reloadConsume;
	private final ReloadType reloadType;

	// Information-durability configuration
	private int durabilityOnShot;
	private int durabilityOnRepair;

	// Shoot-weapon consume configuration
	private int weaponShotConsumeTime;

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
	private SoundConfiguration defaultShotSound;
	private SoundConfiguration customShotSound;
	// empty mag
	private SoundConfiguration defaultMagSound;
	private SoundConfiguration customMagSound;

	// Reload-sound configuration
	private SoundConfiguration reloadDefaultSoundBefore;
	private SoundConfiguration reloadDefaultSoundAfter;
	private SoundConfiguration reloadCustomSoundStart;
	private SoundConfiguration reloadCustomSoundMid;
	private SoundConfiguration reloadCustomSoundEnd;

	// Reload-action bar configuration
	private String reloadActionBarReloading;
	private String reloadActionBarOpening;

	public ItemStack give() {
		ItemBuilder builder = new ItemBuilder(material);

		builder.setDisplayName(displayName).setLore(lore).setDurability(durability);
		builder.addTag("weapon", name).addTag("mag", reloadCapacity);
		builder.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

		return builder.build();
	}

	@Override
	public String toString() {
		return String.format("Weapon{name='%s',displayName='%s',material=%s,damage=%.2f,ammo=%s}", name, displayName,
							 material, projectileDamage, reloadAmmoType);
	}

}
