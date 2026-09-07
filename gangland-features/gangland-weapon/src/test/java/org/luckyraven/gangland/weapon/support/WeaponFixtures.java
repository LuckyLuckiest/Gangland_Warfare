package org.luckyraven.gangland.weapon.support;

import org.bukkit.Material;
import org.luckyraven.gangland.weapon.SelectiveFire;
import org.luckyraven.gangland.weapon.ammo.Ammunition;
import org.luckyraven.gangland.weapon.dto.AmmunitionData;
import org.luckyraven.gangland.weapon.dto.BiologicalData;
import org.luckyraven.gangland.weapon.dto.IncendiaryData;
import org.luckyraven.gangland.weapon.dto.MeleeData;
import org.luckyraven.gangland.weapon.dto.ProjectileData;
import org.luckyraven.gangland.weapon.dto.ReloadData;
import org.luckyraven.gangland.weapon.dto.ThrowableData;
import org.luckyraven.gangland.weapon.projectile.ProjectileType;
import org.luckyraven.gangland.weapon.reload.ReloadType;
import org.luckyraven.gangland.weapon.types.WeaponType;
import org.luckyraven.gangland.weapon.types.biological.BiologicalWeapon;
import org.luckyraven.gangland.weapon.types.gun.GunWeapon;
import org.luckyraven.gangland.weapon.types.incendiary.IncendiaryWeapon;
import org.luckyraven.gangland.weapon.types.melee.MeleeWeapon;
import org.luckyraven.gangland.weapon.types.throwable.ThrowableWeapon;

import java.util.List;
import java.util.UUID;

/**
 * Hand-rolled factories for constructing minimal, valid instances of each of the five {@code Weapon} subclasses
 * (gun, melee, throwable, incendiary, biological) so tests can exercise shared {@code Weapon} behaviour across every
 * action type, per project convention (see {@code feedback_unify_across_weapon_types}).
 *
 * <p>Every factory sets {@code Ammunition:} and {@code Reload: {Type: instant}} data so
 * {@code isMagazineEmpty()}/{@code requiresReload()} behave the way they do for a real shipped weapon YAML — every
 * shipped file pairs the two sections together.
 */
public final class WeaponFixtures {

	private WeaponFixtures() {
	}

	public static Ammunition ammo(String key) {
		return new Ammunition(key, "&7" + key, Material.COAL, 0, List.of());
	}

	public static AmmunitionData ammoData(int maxMag, int consumeRate, int restore) {
		return new AmmunitionData(ammo("test_ammo"), maxMag, consumeRate, restore);
	}

	public static ReloadData instantReload() {
		return ReloadData.builder().cooldown(20).type(ReloadType.getType("instant")).build();
	}

	public static GunWeapon gunWeapon(int maxMag, int consumedPerShot) {
		return gunWeapon(maxMag, consumedPerShot, (short) 100);
	}

	/**
	 * Overload exposing {@code Durability.Base} so tests can exercise {@code DurabilityCalculator}'s zero-max-durability
	 * edge case (Observation #33, weapons.md).
	 */
	public static GunWeapon gunWeapon(int maxMag, int consumedPerShot, short durability) {
		ProjectileData projectile = ProjectileData.builder()
				.speed(3.0)
				.type(ProjectileType.BULLET)
				.damage(5.0)
				.consumed(consumedPerShot)
				.perShot(1)
				.cooldown(4)
				.distance(60)
				.particle(false)
				.gravity(0.0)
				.build();

		return new GunWeapon(UUID.randomUUID(), "test_gun", "&fTest Gun", WeaponType.GUN, Material.IRON_HOE, 0,
		                     durability, List.of(), false, null, SelectiveFire.SINGLE, 0, projectile,
		                     instantReload(), ammoData(maxMag, 1, maxMag));
	}

	public static MeleeWeapon meleeWeapon(int maxMag) {
		MeleeData melee = new MeleeData(8.0, 3.0, 10, 0.5);
		return new MeleeWeapon(UUID.randomUUID(), "test_knife", "&fTest Knife", WeaponType.MELEE, Material.IRON_HOE,
		                       0, (short) 50, List.of(), false, null, melee, instantReload(),
		                       ammoData(maxMag, 1, maxMag));
	}

	public static ThrowableWeapon throwableWeapon(int maxMag) {
		ThrowableData throwable = new ThrowableData();
		throwable.setFuseTime(60);
		throwable.setExplosionRadius(3.0);
		throwable.setExplosionDamage(6);
		return new ThrowableWeapon(UUID.randomUUID(), "test_grenade", "&fTest Grenade", WeaponType.THROWABLE,
		                           Material.IRON_HOE, 0, (short) 1, List.of(), false, null, throwable,
		                           instantReload(), ammoData(maxMag, 1, maxMag));
	}

	public static IncendiaryWeapon incendiaryWeapon(int maxMag, int consumeRate) {
		IncendiaryData incendiary = new IncendiaryData(30.0, 5.0, 60, 2, consumeRate);
		return new IncendiaryWeapon(UUID.randomUUID(), "test_flamer", "&fTest Flamer", WeaponType.INCENDIARY,
		                            Material.IRON_HOE, 0, (short) 100, List.of(), false, null, incendiary,
		                            instantReload(), ammoData(maxMag, 1, maxMag));
	}

	public static BiologicalWeapon biologicalWeapon(int maxMag) {
		BiologicalData biological = new BiologicalData(20, 3, List.of("BLINDNESS-100-1"), 30.0, 4.0);
		return new BiologicalWeapon(UUID.randomUUID(), "test_biogun", "&fTest Biogun", WeaponType.BIOLOGICAL,
		                            Material.IRON_HOE, 0, (short) 100, List.of(), false, null, biological,
		                            instantReload(), ammoData(maxMag, 1, maxMag));
	}

}
