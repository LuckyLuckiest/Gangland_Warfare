package org.luckyraven.gangland.weapon.types.melee;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.dto.AmmunitionData;
import org.luckyraven.gangland.weapon.dto.MeleeData;
import org.luckyraven.gangland.weapon.dto.ReloadData;
import org.luckyraven.gangland.weapon.types.WeaponType;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class MeleeWeapon extends Weapon {

	private final MeleeData meleeData;

	public MeleeWeapon(UUID uuid, String name, String displayName, WeaponType category, Material material,
	                   int customModelData, short durability, List<String> lore, boolean dropHologram,
	                   @Nullable List<String> deathMessages, MeleeData meleeData,
	                   @Nullable ReloadData reloadData, @Nullable AmmunitionData ammunitionData) {
		super(uuid, name, displayName, category, material, customModelData, durability, lore, dropHologram,
		      deathMessages, reloadData, ammunitionData);
		this.meleeData = meleeData;
	}

	@Override
	public MeleeWeapon copyWithUUID(UUID newUuid) {
		MeleeWeapon copy = (MeleeWeapon) super.clone();
		copy.setUUID(newUuid);
		return copy;
	}

	@Override
	public MeleeWeapon clone() {
		// No extra mutable fields — Weapon.clone() handles everything.
		return (MeleeWeapon) super.clone();
	}

}
