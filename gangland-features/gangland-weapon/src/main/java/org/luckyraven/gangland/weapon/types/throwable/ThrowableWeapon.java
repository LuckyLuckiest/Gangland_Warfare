package org.luckyraven.gangland.weapon.types.throwable;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.dto.AmmunitionData;
import org.luckyraven.gangland.weapon.dto.ReloadData;
import org.luckyraven.gangland.weapon.dto.ThrowableData;
import org.luckyraven.gangland.weapon.types.WeaponType;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ThrowableWeapon extends Weapon {

	private final ThrowableData throwableData;

	public ThrowableWeapon(UUID uuid, String name, String displayName, WeaponType category, Material material,
	                       int customModelData, short durability, List<String> lore, boolean dropHologram,
	                       @Nullable List<String> deathMessages, ThrowableData throwableData,
	                       @Nullable ReloadData reloadData, @Nullable AmmunitionData ammunitionData) {
		super(uuid, name, displayName, category, material, customModelData, durability, lore, dropHologram,
		      deathMessages, reloadData, ammunitionData);
		this.throwableData = throwableData;
	}

	@Override
	public ThrowableWeapon copyWithUUID(UUID newUuid) {
		ThrowableWeapon copy = (ThrowableWeapon) super.clone();
		copy.setUUID(newUuid);
		return copy;
	}

	@Override
	public ThrowableWeapon clone() {
		// No extra mutable fields — Weapon.clone() handles everything.
		return (ThrowableWeapon) super.clone();
	}

}
